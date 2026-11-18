package com.yiyun.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyun.mapper.BookingOrderMapper;
import com.yiyun.mapper.ExperienceMapper;
import com.yiyun.mapper.HeritageMapper;
import com.yiyun.model.entity.BookingOrder;
import com.yiyun.model.entity.Experience;
import com.yiyun.model.entity.Heritage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingOrderMapper bookingOrderMapper;
    private final ExperienceMapper experienceMapper;
    private final HeritageMapper heritageMapper;
    private final ExperienceService experienceService;

    public BookingOrder createBooking(BookingOrder order) {
        log.info("创建预约请求: experienceId={}, people={}, contactName={}, contactPhone={}", 
                order.getExperienceId(), order.getPeople(), order.getContactName(), order.getContactPhone());
        
        Experience experience = experienceMapper.selectById(order.getExperienceId());
        if (experience == null) {
            throw new RuntimeException("体验项目不存在");
        }
        
        if (!"APPROVED".equals(experience.getStatus())) {
            throw new RuntimeException("体验项目暂未开放预约");
        }
        
        // 使用 booked_num 字段检查剩余名额
        int currentBooked = experience.getBookedNum() != null ? experience.getBookedNum() : 0;
        log.info("当前已预约人数: {}, 本次预约人数: {}, 总名额: {}", 
                currentBooked, order.getPeople(), experience.getQuota());
        
        if (currentBooked + order.getPeople() > experience.getQuota()) {
            throw new RuntimeException("该体验项目已满员");
        }
        
        LambdaQueryWrapper<BookingOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BookingOrder::getUserId, order.getUserId())
                .eq(BookingOrder::getExperienceId, order.getExperienceId())
                .in(BookingOrder::getStatus, "PENDING", "CONFIRMED");
        
        if (bookingOrderMapper.selectOne(wrapper) != null) {
            throw new RuntimeException("您已预约该体验项目");
        }
        
        String orderNo = "BK" + LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + 
                         String.format("%04d", (int) (Math.random() * 10000));
        order.setOrderNo(orderNo);
        order.setStatus("PENDING");
        order.setTotalAmount(experience.getPrice().multiply(BigDecimal.valueOf(order.getPeople())));
        order.setCreatedAt(LocalDateTime.now());
        bookingOrderMapper.insert(order);
        
        log.info("预约订单创建成功: orderId={}, people={}", order.getId(), order.getPeople());
        
        // 更新已预约人数（本地统计）
        experienceMapper.update(null, new LambdaUpdateWrapper<Experience>()
                .set(Experience::getBookedNum, currentBooked + order.getPeople())
                .eq(Experience::getId, order.getExperienceId()));
        
        log.info("已预约人数更新完成: experienceId={}, bookedNum={}", 
                order.getExperienceId(), currentBooked + order.getPeople());
        
        return order;
    }

    public List<BookingOrder> getMyBookings(Long userId, String status) {
        LambdaQueryWrapper<BookingOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BookingOrder::getUserId, userId);

        if (status != null && !status.isEmpty()) {
            wrapper.eq(BookingOrder::getStatus, status);
        }

        wrapper.orderByDesc(BookingOrder::getCreatedAt);
        List<BookingOrder> bookings = bookingOrderMapper.selectList(wrapper);
        enrichBookingOrders(bookings);
        return bookings;
    }

    public IPage<BookingOrder> adminList(int page, int size, String status) {
        LambdaQueryWrapper<BookingOrder> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(BookingOrder::getStatus, status);
        }
        wrapper.orderByDesc(BookingOrder::getCreatedAt);
        
        Page<BookingOrder> pageParam = new Page<>(page, size);
        return bookingOrderMapper.selectPage(pageParam, wrapper);
    }

    public IPage<BookingOrder> getBookingsByExperienceId(Long experienceId, int page, int size) {
        LambdaQueryWrapper<BookingOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BookingOrder::getExperienceId, experienceId)
               .orderByDesc(BookingOrder::getCreatedAt);
        
        Page<BookingOrder> pageParam = new Page<>(page, size);
        return bookingOrderMapper.selectPage(pageParam, wrapper);
    }

    public IPage<BookingOrder> getBookingsByUserId(Long userId, int page, int size) {
        LambdaQueryWrapper<BookingOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BookingOrder::getUserId, userId)
               .orderByDesc(BookingOrder::getCreatedAt);

        Page<BookingOrder> pageParam = new Page<>(page, size);
        return bookingOrderMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 查询传承人名下体验项目的预约订单（通过 experience.user_id 关联，而非 booking_order.user_id）
     */
    public IPage<BookingOrder> getInheritorBookings(Long inheritorUserId, int page, int size, String status) {
        LambdaQueryWrapper<Experience> expWrapper = new LambdaQueryWrapper<>();
        expWrapper.eq(Experience::getUserId, inheritorUserId);
        expWrapper.select(Experience::getId);
        List<Experience> experiences = experienceMapper.selectList(expWrapper);

        Page<BookingOrder> pageParam = new Page<>(page, size);
        if (experiences.isEmpty()) {
            pageParam.setRecords(List.of());
            pageParam.setTotal(0);
            return pageParam;
        }

        List<Long> experienceIds = experiences.stream().map(Experience::getId).toList();

        LambdaQueryWrapper<BookingOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(BookingOrder::getExperienceId, experienceIds);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(BookingOrder::getStatus, status);
        }
        wrapper.orderByDesc(BookingOrder::getCreatedAt);

        return bookingOrderMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 检查预约订单是否属于该传承人的体验项目
     */
    public boolean isInheritorOfBooking(Long bookingId, Long inheritorUserId) {
        BookingOrder order = bookingOrderMapper.selectById(bookingId);
        if (order == null) return false;

        Experience experience = experienceMapper.selectById(order.getExperienceId());
        if (experience == null) return false;

        return experience.getUserId().equals(inheritorUserId);
    }

    /**
     * 获取传承人预约统计（全部数据，不分页）
     */
    public Map<String, Object> getInheritorBookingStats(Long inheritorUserId) {
        LambdaQueryWrapper<Experience> expWrapper = new LambdaQueryWrapper<>();
        expWrapper.eq(Experience::getUserId, inheritorUserId);
        expWrapper.select(Experience::getId);
        List<Experience> experiences = experienceMapper.selectList(expWrapper);

        Map<String, Object> stats = new java.util.HashMap<>();
        if (experiences.isEmpty()) {
            stats.put("pending", 0);
            stats.put("confirmed", 0);
            stats.put("completed", 0);
            stats.put("totalPeople", 0);
            return stats;
        }

        List<Long> experienceIds = experiences.stream().map(Experience::getId).toList();

        LambdaQueryWrapper<BookingOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(BookingOrder::getExperienceId, experienceIds);
        List<BookingOrder> allBookings = bookingOrderMapper.selectList(wrapper);

        stats.put("pending", allBookings.stream().filter(b -> "PENDING".equals(b.getStatus())).count());
        stats.put("confirmed", allBookings.stream().filter(b -> "CONFIRMED".equals(b.getStatus())).count());
        stats.put("completed", allBookings.stream().filter(b -> "COMPLETED".equals(b.getStatus())).count());
        stats.put("totalPeople", allBookings.stream().mapToInt(b -> b.getPeople() != null ? b.getPeople() : 0).sum());

        return stats;
    }

    public BookingOrder cancelBooking(Long id, Long userId) {
        BookingOrder order = bookingOrderMapper.selectById(id);
        if (order == null) {
            throw new RuntimeException("预约不存在");
        }
        
        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("无权限取消此预约");
        }
        
        // 如果订单已经是取消状态，不重复处理
        if ("CANCELLED".equals(order.getStatus())) {
            throw new RuntimeException("订单已取消");
        }
        
        bookingOrderMapper.update(new LambdaUpdateWrapper<BookingOrder>()
                .set(BookingOrder::getStatus, "CANCELLED")
                .set(BookingOrder::getUpdatedAt, LocalDateTime.now())
                .eq(BookingOrder::getId, id));
        
        // 减少已预约人数（本地统计）
        Experience experience = experienceMapper.selectById(order.getExperienceId());
        int currentBooked = experience.getBookedNum() != null ? experience.getBookedNum() : 0;
        experienceMapper.update(null, new LambdaUpdateWrapper<Experience>()
                .set(Experience::getBookedNum, currentBooked - order.getPeople())
                .eq(Experience::getId, order.getExperienceId()));
        
        log.info("取消预约，已预约人数减少: experienceId={}, people={}, newBookedNum={}", 
                order.getExperienceId(), order.getPeople(), currentBooked - order.getPeople());
        
        return bookingOrderMapper.selectById(id);
    }

    public BookingOrder approveBooking(Long id) {
        BookingOrder order = bookingOrderMapper.selectById(id);
        if (order == null) {
            throw new RuntimeException("预约不存在");
        }
        
        bookingOrderMapper.update(new LambdaUpdateWrapper<BookingOrder>()
                .set(BookingOrder::getStatus, "CONFIRMED")
                .set(BookingOrder::getUpdatedAt, LocalDateTime.now())
                .eq(BookingOrder::getId, id));
        
        return bookingOrderMapper.selectById(id);
    }

    public BookingOrder rejectBooking(Long id, String reason) {
        BookingOrder order = bookingOrderMapper.selectById(id);
        if (order == null) {
            throw new RuntimeException("预约不存在");
        }
        
        bookingOrderMapper.update(new LambdaUpdateWrapper<BookingOrder>()
                .set(BookingOrder::getStatus, "REJECTED")
                .set(BookingOrder::getRemark, reason)
                .set(BookingOrder::getUpdatedAt, LocalDateTime.now())
                .eq(BookingOrder::getId, id));
        
        return bookingOrderMapper.selectById(id);
    }

    public BookingOrder completeBooking(Long id) {
        BookingOrder order = bookingOrderMapper.selectById(id);
        if (order == null) {
            throw new RuntimeException("预约不存在");
        }
        
        // 设置已核销人数为预约人数
        bookingOrderMapper.update(new LambdaUpdateWrapper<BookingOrder>()
                .set(BookingOrder::getStatus, "COMPLETED")
                .set(BookingOrder::getUsedPeople, order.getPeople())
                .set(BookingOrder::getUpdatedAt, LocalDateTime.now())
                .eq(BookingOrder::getId, id));
        
        return bookingOrderMapper.selectById(id);
    }

    public void rateBooking(Long bookingId, Long userId, BigDecimal rating) {
        BookingOrder order = bookingOrderMapper.selectById(bookingId);
        if (order == null) {
            throw new RuntimeException("预约不存在");
        }

        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("无权对此预约评分");
        }

        if (!"COMPLETED".equals(order.getStatus())) {
            throw new RuntimeException("仅可对已完成的预约评分");
        }

        if (order.getRating() != null) {
            throw new RuntimeException("您已对此预约评过分");
        }

        if (rating == null || rating.compareTo(BigDecimal.ONE) < 0 || rating.compareTo(new BigDecimal("5")) > 0) {
            throw new RuntimeException("评分需在1-5之间");
        }

        bookingOrderMapper.update(new LambdaUpdateWrapper<BookingOrder>()
                .set(BookingOrder::getRating, rating)
                .set(BookingOrder::getUpdatedAt, LocalDateTime.now())
                .eq(BookingOrder::getId, bookingId));

        experienceService.updateExperienceRating(order.getExperienceId());
    }

    private void enrichBookingOrders(List<BookingOrder> bookings) {
        for (BookingOrder order : bookings) {
            if (order.getExperienceId() != null) {
                Experience experience = experienceMapper.selectById(order.getExperienceId());
                if (experience != null) {
                    order.setWorkshop(experience.getName());
                    order.setLocation(experience.getLocation());
                    order.setDate(order.getBookingDate());
                    order.setTime(order.getBookingTime());
                    order.setTotal(order.getTotalAmount());
                    order.setHeritageId(experience.getHeritageId());
                    if (experience.getHeritageId() != null) {
                        Heritage heritage = heritageMapper.selectById(experience.getHeritageId());
                        if (heritage != null) {
                            order.setHeritageName(heritage.getName());
                        }
                    }
                }
            }
        }
    }
}
