package com.yiyun.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyun.mapper.ExperienceMapper;
import com.yiyun.mapper.GraphNodeMapper;
import com.yiyun.mapper.GraphRelationshipMapper;
import com.yiyun.mapper.HeritageImageMapper;
import com.yiyun.mapper.HeritageMapper;
import com.yiyun.mapper.JointInheritorWithdrawalMapper;
import com.yiyun.model.dto.ApiResponse;
import com.yiyun.model.entity.Experience;
import com.yiyun.model.entity.GraphNode;
import com.yiyun.model.entity.GraphRelationship;
import com.yiyun.model.entity.Heritage;
import com.yiyun.model.entity.HeritageImage;
import com.yiyun.model.entity.JointInheritorWithdrawal;
import com.yiyun.model.entity.User;
import com.yiyun.service.UserService;
import com.yiyun.service.ViewCountService;
import com.yiyun.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/heritage")
@RequiredArgsConstructor
public class HeritageController {

    private final HeritageMapper heritageMapper;
    private final HeritageImageMapper heritageImageMapper;
    private final ExperienceMapper experienceMapper;
    private final JointInheritorWithdrawalMapper jointInheritorWithdrawalMapper;
    private final GraphNodeMapper graphNodeMapper;
    private final GraphRelationshipMapper graphRelationshipMapper;
    private final UserService userService;
    private final ViewCountService viewCountService;
    private final SecurityUtil securityUtil;
    private final ObjectMapper objectMapper;

    private static final Map<String, String> CATEGORY_DISPLAY = Map.of(
        "传统技艺", "传统技艺",
        "民间美术", "民间美术",
        "戏曲", "戏曲",
        "民俗", "民俗",
        "传统医药", "传统医药",
        "民间音乐", "民间音乐"
    );

    @GetMapping
    public ApiResponse<IPage<Heritage>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String originDynasty,
            @RequestParam(defaultValue = "views") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder
    ) {
        LambdaQueryWrapper<Heritage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Heritage::getStatus, "APPROVED")
               .eq(Heritage::getIsOnline, 1);

        if (category != null && !category.isEmpty()) {
            wrapper.eq(Heritage::getCategory, category);
        }
        if (region != null && !region.isEmpty()) {
            wrapper.like(Heritage::getRegion, region);
        }
        if (level != null && !level.isEmpty()) {
            wrapper.eq(Heritage::getLevel, level);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Heritage::getName, keyword).or().like(Heritage::getSummary, keyword));
        }
        if (originDynasty != null && !originDynasty.isEmpty()) {
            wrapper.eq(Heritage::getOriginDynasty, originDynasty);
        }

        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        switch (sortBy) {
            case "createdAt":
                wrapper.orderBy(true, asc, Heritage::getCreatedAt);
                break;
            default:
                wrapper.orderBy(true, asc, Heritage::getViews);
                break;
        }

        Page<Heritage> pageParam = new Page<>(page, size);
        IPage<Heritage> result = heritageMapper.selectPage(pageParam, wrapper);
        return ApiResponse.success(result);
    }

    @GetMapping("/my")
    public ApiResponse<List<Heritage>> myHeritages(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String status
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        User user = userService.getUserInfo(authentication.getName());
        String nickname = user.getNickname() != null ? user.getNickname() : user.getUsername();

        // 1. 查询自己的项目
        LambdaQueryWrapper<Heritage> ownWrapper = new LambdaQueryWrapper<>();
        ownWrapper.eq(Heritage::getUserId, user.getId());
        if (status != null && !status.isEmpty()) {
            ownWrapper.eq(Heritage::getStatus, status);
        }
        ownWrapper.orderByDesc(Heritage::getCreatedAt);
        List<Heritage> ownList = heritageMapper.selectList(ownWrapper);
        for (Heritage h : ownList) {
            h.setOwnership("OWNER");
        }

        // 2. 查询联合传承人项目（co_inheritors 中包含当前用户昵称）
        LambdaQueryWrapper<Heritage> jointWrapper = new LambdaQueryWrapper<>();
        jointWrapper.ne(Heritage::getUserId, user.getId())
                   .like(Heritage::getCoInheritors, nickname);
        if (status != null && !status.isEmpty()) {
            jointWrapper.eq(Heritage::getStatus, status);
        }
        jointWrapper.orderByDesc(Heritage::getCreatedAt);
        List<Heritage> jointList = heritageMapper.selectList(jointWrapper);
        for (Heritage h : jointList) {
            h.setOwnership("JOINT");
        }

        // 3. 合并
        List<Heritage> result = new ArrayList<>();
        result.addAll(ownList);
        result.addAll(jointList);

        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<Heritage> detail(
            @PathVariable("id") Long heritageId,
            HttpServletRequest request,
            Authentication authentication
    ) {
        Heritage heritage = heritageMapper.selectById(heritageId);
        if (heritage == null) {
            return ApiResponse.error(404, "非遗项目不存在");
        }
        
        if (!"APPROVED".equals(heritage.getStatus()) || !Integer.valueOf(1).equals(heritage.getIsOnline())) {
            return ApiResponse.error(404, "非遗项目不存在");
        }
        
        Long userId = null;
        if (authentication != null) {
            User user = userService.getUserInfo(authentication.getName());
            if (user != null) {
                userId = user.getId();
            }
        }
        
        String clientIp = getClientIp(request);
        
        boolean shouldIncrement = viewCountService.recordView(heritageId, clientIp, userId);
        if (shouldIncrement) {
            int currentViews = heritage.getViews() != null ? heritage.getViews() : 0;
            heritage.setViews(currentViews + 1);
            heritageMapper.updateById(heritage);
        }
        
        return ApiResponse.success(heritage);
    }
    
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    @GetMapping("/{id}/experiences")
    public ApiResponse<List<Experience>> getExperiences(@PathVariable("id") Long heritageId) {
        LambdaQueryWrapper<Experience> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Experience::getHeritageId, heritageId)
               .eq(Experience::getStatus, "APPROVED");
        return ApiResponse.success(experienceMapper.selectList(wrapper));
    }

    @GetMapping("/{id}/images")
    public ApiResponse<List<HeritageImage>> getImages(@PathVariable("id") Long heritageId) {
        LambdaQueryWrapper<HeritageImage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HeritageImage::getHeritageId, heritageId)
               .orderByAsc(HeritageImage::getSortOrder);
        return ApiResponse.success(heritageImageMapper.selectList(wrapper));
    }

    @GetMapping("/search")
    public ApiResponse<List<Heritage>> search(@RequestParam("q") String keyword) {
        LambdaQueryWrapper<Heritage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Heritage::getStatus, "APPROVED")
               .eq(Heritage::getIsOnline, 1)
                .and(w -> w.like(Heritage::getName, keyword).or().like(Heritage::getSummary, keyword));
        return ApiResponse.success(heritageMapper.selectList(wrapper));
    }

    @GetMapping("/categories")
    public ApiResponse<List<String>> getCategories() {
        return ApiResponse.success(List.of(
            "传统技艺", "民间美术", "戏曲", "民俗", "传统医药", "民间音乐"
        ));
    }

    @PostMapping
    public ApiResponse<Heritage> create(
            Authentication authentication,
            @RequestBody Heritage heritage
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        
        User user = userService.getUserInfo(authentication.getName());
        
        heritage.setUserId(user.getId());
        heritage.setStatus("ADMIN".equals(user.getRole()) ? "APPROVED" : "PENDING");
        heritage.setViews(0);
        heritage.setIsOnline(0);
        heritageMapper.insert(heritage);

        // 管理员创建的非遗直接同步到知识图谱
        if ("APPROVED".equals(heritage.getStatus())) {
            syncGraphForHeritage(heritage);
        }

        return ApiResponse.success(heritage);
    }

    @PutMapping("/{id}")
    public ApiResponse<Heritage> update(
            Authentication authentication,
            @PathVariable("id") Long heritageId,
            @RequestBody Heritage heritage
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        
        User user = userService.getUserInfo(authentication.getName());
        Heritage existing = heritageMapper.selectById(heritageId);
        if (existing == null) {
            return ApiResponse.error(404, "非遗项目不存在");
        }
        
        if (!Objects.equals(existing.getUserId(), user.getId()) && !"ADMIN".equals(user.getRole())) {
            return ApiResponse.error(403, "无权限修改");
        }
        
        heritage.setId(heritageId);
        heritageMapper.updateById(heritage);
        return ApiResponse.success(heritageMapper.selectById(heritageId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            Authentication authentication,
            @PathVariable("id") Long heritageId
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        User user = userService.getUserInfo(authentication.getName());
        Heritage heritage = heritageMapper.selectById(heritageId);
        if (heritage == null) {
            return ApiResponse.error(404, "非遗项目不存在");
        }

        if (!Objects.equals(heritage.getUserId(), user.getId()) && !"ADMIN".equals(user.getRole())) {
            return ApiResponse.error(403, "无权限删除");
        }

        heritageMapper.deleteById(heritageId);

        // 同步删除知识图谱节点
        removeGraphForHeritage(heritageId);

        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/toggle-online")
    public ApiResponse<Heritage> toggleOnline(
            Authentication authentication,
            @PathVariable("id") Long heritageId
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        User user = userService.getUserInfo(authentication.getName());
        Heritage heritage = heritageMapper.selectById(heritageId);
        if (heritage == null) {
            return ApiResponse.error(404, "非遗项目不存在");
        }

        if (!Objects.equals(heritage.getUserId(), user.getId()) && !"ADMIN".equals(user.getRole())) {
            return ApiResponse.error(403, "无权限操作");
        }

        if (!"APPROVED".equals(heritage.getStatus())) {
            return ApiResponse.error(400, "审核通过后才能上架/下架");
        }

        int newOnline = Integer.valueOf(1).equals(heritage.getIsOnline()) ? 0 : 1;
        heritage.setIsOnline(newOnline);
        heritageMapper.updateById(heritage);

        // 上下架同步知识图谱
        if (newOnline == 1) {
            syncGraphForHeritage(heritage);
        } else {
            removeGraphForHeritage(heritageId);
        }

        return ApiResponse.success(heritage);
    }

    @PostMapping("/{id}/withdraw")
    public ApiResponse<Void> withdraw(
            Authentication authentication,
            @PathVariable("id") Long heritageId
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        User user = userService.getUserInfo(authentication.getName());
        Heritage heritage = heritageMapper.selectById(heritageId);
        if (heritage == null) {
            return ApiResponse.error(404, "非遗项目不存在");
        }

        String nickname = user.getNickname() != null ? user.getNickname() : user.getUsername();

        // 从 co_inheritors JSON 数组中移除当前用户
        String coInheritors = heritage.getCoInheritors();
        if (coInheritors == null || !coInheritors.contains(nickname)) {
            return ApiResponse.error(400, "您不是该项目的联合传承人");
        }

        try {
            String[] names = objectMapper.readValue(coInheritors, String[].class);
            List<String> nameList = new ArrayList<>();
            for (String name : names) {
                if (!name.equals(nickname)) {
                    nameList.add(name);
                }
            }
            heritage.setCoInheritors(objectMapper.writeValueAsString(nameList));
        } catch (Exception e) {
            return ApiResponse.error(500, "解析联合传承人数据失败");
        }

        heritageMapper.updateById(heritage);

        // 记录退出申请
        JointInheritorWithdrawal withdrawal = new JointInheritorWithdrawal();
        withdrawal.setHeritageId(heritageId);
        withdrawal.setUserId(user.getId());
        withdrawal.setStatus("APPROVED");
        jointInheritorWithdrawalMapper.insert(withdrawal);

        return ApiResponse.success(null);
    }

    @GetMapping("/admin/list")
    public ApiResponse<IPage<Heritage>> adminList(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        securityUtil.ensureAdmin(authentication);
        
        LambdaQueryWrapper<Heritage> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Heritage::getStatus, status);
        }
        wrapper.orderByDesc(Heritage::getCreatedAt);
        
        Page<Heritage> pageParam = new Page<>(page, size);
        return ApiResponse.success(heritageMapper.selectPage(pageParam, wrapper));
    }

    @PutMapping("/admin/{id}/approve")
    public ApiResponse<Heritage> approve(
            Authentication authentication,
            @PathVariable("id") Long heritageId
    ) {
        securityUtil.ensureAdmin(authentication);
        Heritage heritage = heritageMapper.selectById(heritageId);
        if (heritage == null) {
            return ApiResponse.error(404, "非遗项目不存在");
        }
        heritageMapper.update(new LambdaUpdateWrapper<Heritage>()
                .set(Heritage::getStatus, "APPROVED")
                .set(Heritage::getIsOnline, 1)
                .eq(Heritage::getId, heritageId));

        // 审批通过后同步到知识图谱
        Heritage approved = heritageMapper.selectById(heritageId);
        syncGraphForHeritage(approved);

        return ApiResponse.success(approved);
    }

    @PutMapping("/admin/{id}/reject")
    public ApiResponse<Heritage> reject(
            Authentication authentication,
            @PathVariable("id") Long heritageId,
            @RequestBody(required = false) Map<String, String> request
    ) {
        securityUtil.ensureAdmin(authentication);
        Heritage heritage = heritageMapper.selectById(heritageId);
        if (heritage == null) {
            return ApiResponse.error(404, "非遗项目不存在");
        }
        heritageMapper.update(new LambdaUpdateWrapper<Heritage>()
                .set(Heritage::getStatus, "REJECTED")
                .eq(Heritage::getId, heritageId));
        return ApiResponse.success(heritageMapper.selectById(heritageId));
    }

    // ==================== 知识图谱同步 ====================

    /**
     * 将已审批的非遗项目同步到知识图谱（创建 HERITAGE 节点 + 关联节点 + 关系）
     */
    private void syncGraphForHeritage(Heritage heritage) {
        // 先清理旧数据（处理重新审批/重新上架的情况）
        removeGraphForHeritage(heritage.getId());

        String heritageNodeId = "H-" + heritage.getId();

        // 1. 创建非遗节点
        GraphNode heritageNode = new GraphNode();
        heritageNode.setNodeId(heritageNodeId);
        heritageNode.setName(heritage.getName());
        heritageNode.setType("HERITAGE");
        heritageNode.setLevel(heritage.getLevel());
        heritageNode.setCategory(heritage.getCategory());
        heritageNode.setDescription(heritage.getSummary() != null ? heritage.getSummary() : heritage.getDescription());
        try {
            Map<String, Object> props = new HashMap<>();
            if (heritage.getImageUrl() != null) props.put("imageUrl", heritage.getImageUrl());
            if (heritage.getVideoUrl() != null) props.put("videoUrl", heritage.getVideoUrl());
            if (heritage.getViews() != null) props.put("views", heritage.getViews());
            if (!props.isEmpty()) {
                heritageNode.setProperties(objectMapper.writeValueAsString(props));
            }
        } catch (Exception ignored) {}
        graphNodeMapper.insert(heritageNode);

        // 2. 关联地区
        if (heritage.getRegion() != null && !heritage.getRegion().isBlank()) {
            String regionId = ensureNode(heritage.getRegion(), "REGION", null, null, heritage.getRegion());
            createRelationship(heritageNodeId, regionId, "ORIGIN_FROM", "发源于");
        }

        // 3. 关联传承人
        if (heritage.getInheritorName() != null && !heritage.getInheritorName().isBlank()
                && !"无特定传承人".equals(heritage.getInheritorName())) {
            String inheritorId = ensureNode(heritage.getInheritorName(), "INHERITOR", null, null, heritage.getInheritorName());
            createRelationship(heritageNodeId, inheritorId, "INHERITS", "传承人是");
        }

        // 4. 关联朝代
        if (heritage.getOriginDynasty() != null && !heritage.getOriginDynasty().isBlank()) {
            String dynastyId = ensureNode(heritage.getOriginDynasty(), "DYNASTY", null, null, heritage.getOriginDynasty());
            createRelationship(heritageNodeId, dynastyId, "ORIGIN_DYNASTY", "起源朝代");
        }

        // 5. 关联技艺分类
        if (heritage.getCategory() != null && !heritage.getCategory().isBlank()) {
            String skillName = CATEGORY_DISPLAY.getOrDefault(heritage.getCategory(), heritage.getCategory());
            String skillId = ensureNode(skillName, "SKILL", null, null, skillName);
            createRelationship(heritageNodeId, skillId, "USES_SKILL", "使用技艺");
        }
    }

    /**
     * 从知识图谱中移除非遗项目节点（关系通过外键级联删除）
     */
    private void removeGraphForHeritage(Long heritageId) {
        String heritageNodeId = "H-" + heritageId;
        graphRelationshipMapper.delete(new LambdaQueryWrapper<GraphRelationship>()
                .eq(GraphRelationship::getSourceId, heritageNodeId)
                .or()
                .eq(GraphRelationship::getTargetId, heritageNodeId));
        graphNodeMapper.delete(new LambdaQueryWrapper<GraphNode>()
                .eq(GraphNode::getNodeId, heritageNodeId));
    }

    /**
     * 按名称和类型查找或创建节点，返回 nodeId
     */
    private String ensureNode(String name, String type, String level, String category, String description) {
        GraphNode existing = graphNodeMapper.selectOne(
                new LambdaQueryWrapper<GraphNode>()
                        .eq(GraphNode::getName, name)
                        .eq(GraphNode::getType, type));
        if (existing != null) {
            return existing.getNodeId();
        }

        String nodeId = type.charAt(0) + "-" + System.currentTimeMillis();
        GraphNode node = new GraphNode();
        node.setNodeId(nodeId);
        node.setName(name);
        node.setType(type);
        node.setLevel(level);
        node.setCategory(category);
        node.setDescription(description);
        graphNodeMapper.insert(node);
        return nodeId;
    }

    /**
     * 创建节点间关系（忽略重复）
     */
    private void createRelationship(String sourceId, String targetId, String relationType, String label) {
        try {
            GraphRelationship rel = new GraphRelationship();
            rel.setSourceId(sourceId);
            rel.setTargetId(targetId);
            rel.setRelationType(relationType);
            rel.setLabel(label);
            graphRelationshipMapper.insert(rel);
        } catch (Exception ignored) {
            // 唯一约束冲突时忽略（关系已存在）
        }
    }
}