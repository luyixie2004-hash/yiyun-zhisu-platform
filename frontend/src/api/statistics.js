import request from './request'

// 获取仪表盘今日聚合数据
export function getDashboardStats() {
  return request({
    url: '/api/statistics/dashboard',
    method: 'get'
  })
}

// 获取非遗项目总数
export function getHeritageCount() {
  return request({
    url: '/api/statistics/heritage/count',
    method: 'get'
  })
}

// 获取非遗分类占比数据
export function getHeritageCategoryStats() {
  return request({
    url: '/api/statistics/heritage/categories',
    method: 'get'
  })
}

// 获取热门非遗TOP5
export function getTopHeritages() {
  return request({
    url: '/api/statistics/heritage/top5',
    method: 'get'
  })
}

// 获取AIGC生成类型统计
export function getAIGCTypeStats() {
  return request({
    url: '/api/statistics/aigc/types',
    method: 'get'
  })
}

// 获取月度预约趋势
export function getBookingTrend() {
  return request({
    url: '/api/statistics/booking/trend',
    method: 'get'
  })
}

// 获取近7天日活统计
export function getDailyActiveStats() {
  return request({
    url: '/api/statistics/daily/active',
    method: 'get'
  })
}

// 获取用户总数
export function getUserCount() {
  return request({
    url: '/api/statistics/user/count',
    method: 'get'
  })
}

// 获取作品总数（传承人作品）
export function getArtworkCount() {
  return request({
    url: '/api/statistics/artwork/count',
    method: 'get'
  })
}

// 获取预约总数
export function getBookingCount() {
  return request({
    url: '/api/statistics/booking/count',
    method: 'get'
  })
}

// 获取体验项目总数
export function getExperienceCount() {
  return request({
    url: '/api/statistics/experience/count',
    method: 'get'
  })
}

// 获取传承人总数
export function getInheritorCount() {
  return request({
    url: '/api/statistics/inheritor/count',
    method: 'get'
  })
}

// 获取非遗项目按地区分布统计
export function getHeritageRegionCounts() {
  return request({
    url: '/api/statistics/heritage/regions',
    method: 'get'
  })
}

// 获取首页统计数据
export function getHomeStats() {
  return request({
    url: '/api/statistics/home',
    method: 'get'
  })
}

// 获取非遗项目按年份+分类趋势数据
export function getHeritageYearlyTrend() {
  return request({
    url: '/api/statistics/heritage/trend',
    method: 'get'
  })
}

// 获取非遗项目按级别分布统计
export function getHeritageLevelStats() {
  return request({
    url: '/api/statistics/heritage/levels',
    method: 'get'
  })
}

// 获取非遗项目按省份聚合统计（短名）
export function getHeritageProvinceCounts() {
  return request({
    url: '/api/statistics/heritage/provinces',
    method: 'get'
  })
}

// 获取社区帖子总数
export function getPostCount() {
  return request({
    url: '/api/statistics/post/count',
    method: 'get'
  })
}

// 获取社区活动总数
export function getActivityCount() {
  return request({
    url: '/api/statistics/activity/count',
    method: 'get'
  })
}
