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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExperienceService {

    private final ExperienceMapper experienceMapper;
    private final HeritageMapper heritageMapper;
    private final BookingOrderMapper bookingOrderMapper;

    public List<Experience> getExperiencesByHeritageId(Long heritageId) {
        List<Experience> experiences = experienceMapper.selectList(
                new LambdaQueryWrapper<Experience>()
                        .eq(Experience::getHeritageId, heritageId)
                        .eq(Experience::getStatus, "APPROVED")
        );
        populateInheritorNames(experiences);
        populateRemainingQuota(experiences);
        return experiences;
    }

    public Experience getExperienceById(Long id) {
        Experience experience = experienceMapper.selectById(id);
        populateInheritorName(experience);
        return experience;
    }

    public Experience createExperience(Experience experience, Long userId) {
        experience.setUserId(userId);
        experience.setStatus("PENDING");
        experience.setCreatedAt(LocalDateTime.now());
        experienceMapper.insert(experience);
        return experience;
    }

    public Experience updateExperience(Long id, Experience experience, Long userId) {
        Experience existing = experienceMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("体验项目不存在");
        }
        
        if (!existing.getUserId().equals(userId)) {
            throw new RuntimeException("无权限修改此体验项目");
        }
        
        experience.setId(id);
        experience.setStatus("PENDING");
        experience.setUpdatedAt(LocalDateTime.now());
        experienceMapper.updateById(experience);
        
        return experienceMapper.selectById(id);
    }

    public void deleteExperience(Long id, Long userId) {
        Experience experience = experienceMapper.selectById(id);
        if (experience == null) {
            throw new RuntimeException("体验项目不存在");
        }
        
        if (!experience.getUserId().equals(userId)) {
            throw new RuntimeException("无权限删除此体验项目");
        }
        
        experienceMapper.deleteById(id);
    }

    public List<Experience> getActiveExperiences() {
        List<Experience> experiences = experienceMapper.selectList(
                new LambdaQueryWrapper<Experience>()
                        .eq(Experience::getStatus, "APPROVED")
        );
        populateInheritorNames(experiences);
        populateRemainingQuota(experiences);
        return experiences;
    }

    public List<Experience> searchExperiences(String keyword) {
        List<Experience> experiences = experienceMapper.selectList(
                new LambdaQueryWrapper<Experience>()
                        .like(Experience::getName, keyword)
                        .eq(Experience::getStatus, "APPROVED")
        );
        populateInheritorNames(experiences);
        populateRemainingQuota(experiences);
        return experiences;
    }

    public IPage<Experience> adminList(int page, int size, String status) {
        LambdaQueryWrapper<Experience> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Experience::getStatus, status);
        }
        wrapper.orderByDesc(Experience::getCreatedAt);
        
        Page<Experience> pageParam = new Page<>(page, size);
        IPage<Experience> result = experienceMapper.selectPage(pageParam, wrapper);
        populateInheritorNames(result.getRecords());
        return result;
    }

    public IPage<Experience> getExperiencesByUserId(Long userId, int page, int size) {
        LambdaQueryWrapper<Experience> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Experience::getUserId, userId)
               .orderByDesc(Experience::getCreatedAt);

        Page<Experience> pageParam = new Page<>(page, size);
        IPage<Experience> result = experienceMapper.selectPage(pageParam, wrapper);
        populateInheritorNames(result.getRecords());
        return result;
    }

    public IPage<Experience> getApprovedExperiencesByUserId(Long userId, int page, int size) {
        LambdaQueryWrapper<Experience> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Experience::getUserId, userId)
               .eq(Experience::getStatus, "APPROVED")
               .orderByDesc(Experience::getCreatedAt);
        Page<Experience> pageParam = new Page<>(page, size);
        IPage<Experience> result = experienceMapper.selectPage(pageParam, wrapper);
        populateInheritorNames(result.getRecords());
        return result;
    }

    public Experience approveExperience(Long id) {
        Experience experience = experienceMapper.selectById(id);
        if (experience == null) {
            throw new RuntimeException("体验项目不存在");
        }
        
        experienceMapper.update(new LambdaUpdateWrapper<Experience>()
                .set(Experience::getStatus, "APPROVED")
                .eq(Experience::getId, id));
        
        return experienceMapper.selectById(id);
    }

    public Experience rejectExperience(Long id) {
        Experience experience = experienceMapper.selectById(id);
        if (experience == null) {
            throw new RuntimeException("体验项目不存在");
        }
        
        experienceMapper.update(new LambdaUpdateWrapper<Experience>()
                .set(Experience::getStatus, "REJECTED")
                .eq(Experience::getId, id));
        
        return experienceMapper.selectById(id);
    }

    public void deactivateExperience(Long id, Long userId) {
        Experience experience = experienceMapper.selectById(id);
        if (experience == null) {
            throw new RuntimeException("体验项目不存在");
        }
        
        if (!experience.getUserId().equals(userId)) {
            throw new RuntimeException("无权限操作此体验项目");
        }
        
        experienceMapper.update(new LambdaUpdateWrapper<Experience>()
                .set(Experience::getStatus, "INACTIVE")
                .eq(Experience::getId, id));
    }

    private void populateInheritorNames(List<Experience> experiences) {
        for (Experience experience : experiences) {
            populateInheritorName(experience);
        }
    }

    private void populateInheritorName(Experience experience) {
        if (experience != null && experience.getHeritageId() != null) {
            Heritage heritage = heritageMapper.selectById(experience.getHeritageId());
            if (heritage != null && heritage.getInheritorName() != null) {
                experience.setInheritorName(heritage.getInheritorName());
            }
        }
    }

    public void updateExperienceRating(Long experienceId) {
        LambdaQueryWrapper<BookingOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BookingOrder::getExperienceId, experienceId)
               .eq(BookingOrder::getStatus, "COMPLETED")
               .isNotNull(BookingOrder::getRating);

        List<BookingOrder> ratedOrders = bookingOrderMapper.selectList(wrapper);
        if (ratedOrders.isEmpty()) {
            return;
        }

        BigDecimal avgRating = ratedOrders.stream()
                .map(BookingOrder::getRating)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(ratedOrders.size()), 1, java.math.RoundingMode.HALF_UP);

        experienceMapper.update(new LambdaUpdateWrapper<Experience>()
                .set(Experience::getRating, avgRating)
                .eq(Experience::getId, experienceId));
    }

    private void populateRemainingQuota(List<Experience> experiences) {
        for (Experience experience : experiences) {
            if (experience != null && experience.getQuota() != null) {
                // 使用本地的 bookedNum 字段（由 BookingService 维护）
                int bookedNum = experience.getBookedNum() != null ? experience.getBookedNum() : 0;
                experience.setRemainingQuota(experience.getQuota() - bookedNum);
            }
        }
    }

}
