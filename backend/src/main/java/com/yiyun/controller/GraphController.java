package com.yiyun.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyun.mapper.GraphNodeMapper;
import com.yiyun.mapper.GraphRelationshipMapper;
import com.yiyun.mapper.HeritageMapper;
import com.yiyun.mapper.UserMapper;
import com.yiyun.model.dto.ApiResponse;
import com.yiyun.model.entity.GraphNode;
import com.yiyun.model.entity.GraphRelationship;
import com.yiyun.model.entity.Heritage;
import com.yiyun.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphNodeMapper graphNodeMapper;
    private final GraphRelationshipMapper graphRelationshipMapper;
    private final HeritageMapper heritageMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/full")
    public ApiResponse<Map<String, Object>> getFullGraph() {
        List<GraphNode> nodes = graphNodeMapper.selectList(null);
        List<GraphRelationship> relationships = graphRelationshipMapper.selectList(null);

        // Build lookup maps for enrichment
        Map<String, Heritage> heritageByName = heritageMapper.selectList(null).stream()
                .collect(Collectors.toMap(Heritage::getName, h -> h, (a, b) -> a));
        Map<String, User> userByNickname = userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getRole, "INHERITOR")
        ).stream().collect(Collectors.toMap(User::getNickname, u -> u, (a, b) -> a));

        // Enrich nodes with image URLs and link IDs
        for (GraphNode node : nodes) {
            Map<String, Object> props = new HashMap<>();
            if ("非遗项目".equals(node.getType())) {
                Heritage h = heritageByName.get(node.getName());
                if (h != null) {
                    props.put("imageUrl", h.getImageUrl());
                    props.put("heritageId", h.getId());
                }
            } else if ("传承人".equals(node.getType())) {
                User u = userByNickname.get(node.getName());
                if (u != null) {
                    props.put("imageUrl", u.getAvatar());
                    props.put("userId", u.getId());
                }
            }
            if (!props.isEmpty()) {
                try {
                    node.setProperties(objectMapper.writeValueAsString(props));
                } catch (JsonProcessingException e) {
                    // ignore
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nodes", nodes);
        result.put("edges", relationships);
        return ApiResponse.success(result);
    }

    @GetMapping("/node/{nodeId}")
    public ApiResponse<Map<String, Object>> getNodeNeighbors(@PathVariable String nodeId) {
        LambdaQueryWrapper<GraphRelationship> outWrapper = new LambdaQueryWrapper<>();
        outWrapper.eq(GraphRelationship::getSourceId, nodeId);
        List<GraphRelationship> outEdges = graphRelationshipMapper.selectList(outWrapper);

        LambdaQueryWrapper<GraphRelationship> inWrapper = new LambdaQueryWrapper<>();
        inWrapper.eq(GraphRelationship::getTargetId, nodeId);
        List<GraphRelationship> inEdges = graphRelationshipMapper.selectList(inWrapper);

        List<String> neighborIds = outEdges.stream()
                .map(GraphRelationship::getTargetId)
                .toList();
        neighborIds.addAll(inEdges.stream()
                .map(GraphRelationship::getSourceId)
                .toList());

        LambdaQueryWrapper<GraphNode> nodeWrapper = new LambdaQueryWrapper<>();
        nodeWrapper.in(GraphNode::getNodeId, neighborIds);
        List<GraphNode> neighbors = graphNodeMapper.selectList(nodeWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("node", graphNodeMapper.selectOne(new LambdaQueryWrapper<GraphNode>().eq(GraphNode::getNodeId, nodeId)));
        result.put("neighbors", neighbors);
        result.put("edges", outEdges);
        
        return ApiResponse.success(result);
    }

    @GetMapping("/search")
    public ApiResponse<List<GraphNode>> searchNodes(@RequestParam String keyword) {
        LambdaQueryWrapper<GraphNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(GraphNode::getName, keyword);
        return ApiResponse.success(graphNodeMapper.selectList(wrapper));
    }

    @GetMapping("/type/{type}")
    public ApiResponse<List<GraphNode>> getNodesByType(@PathVariable String type) {
        LambdaQueryWrapper<GraphNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GraphNode::getType, type);
        return ApiResponse.success(graphNodeMapper.selectList(wrapper));
    }
}