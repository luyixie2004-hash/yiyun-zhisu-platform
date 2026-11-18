/**
 * 非遗项目分类（10类，与 heritageData.js 及后端保持一致）
 */
export const CATEGORY_MAP = {
  '民间文学': '民间文学',
  '传统音乐': '传统音乐',
  '传统舞蹈': '传统舞蹈',
  '传统戏剧': '传统戏剧',
  '曲艺': '曲艺',
  '传统体育、游艺与杂技': '传统体育、游艺与杂技',
  '传统美术': '传统美术',
  '传统技艺': '传统技艺',
  '传统医药': '传统医药',
  '民俗': '民俗',
}

export const CATEGORY_OPTIONS = Object.entries(CATEGORY_MAP).map(([value, label]) => ({ value, label }))

export const CATEGORY_CODE_TO_NAME = CATEGORY_MAP

export const CATEGORY_NAME_TO_CODE = Object.fromEntries(
  Object.entries(CATEGORY_MAP).map(([k, v]) => [v, k])
)

/**
 * 非遗级别
 */
export const LEVEL_MAP = {
  '国家级': '国家级',
  '省级': '省级',
  '市级': '市级',
}

export const LEVEL_OPTIONS = Object.entries(LEVEL_MAP).map(([value, label]) => ({ value, label }))

export const LEVEL_CODE_TO_NAME = LEVEL_MAP

export const LEVEL_NAME_TO_CODE = Object.fromEntries(
  Object.entries(LEVEL_MAP).map(([k, v]) => [v, k])
)

/**
 * 体验类型
 */
export const EXPERIENCE_TYPE_MAP = {
  '线下工坊': '线下工坊',
  '线上活动': '线上活动',
}

export const EXPERIENCE_TYPE_OPTIONS = Object.entries(EXPERIENCE_TYPE_MAP).map(([value, label]) => ({ value, label }))

export const EXPERIENCE_TYPE_NAME_TO_CODE = Object.fromEntries(
  Object.entries(EXPERIENCE_TYPE_MAP).map(([k, v]) => [v, k])
)

/**
 * 用户角色
 */
export const ROLE_MAP = {
  ADMIN: '管理员',
  INHERITOR: '传承人',
  USER: '普通用户',
}

/**
 * 图谱节点类型
 */
export const GRAPH_NODE_TYPE_MAP = {
  '非遗项目': '非遗项目',
  '传承地区': '传承地区',
  '传承人': '传承人',
  '技艺': '技艺',
  '朝代': '朝代',
}

/**
 * 图谱技艺节点
 */
export const SKILL_NODE_MAP = {
  S001: '传统技艺',
  S002: '民间美术',
  S003: '戏曲表演',
  S004: '民俗活动',
  S005: '传统医药',
  S006: '民间音乐',
}

/**
 * 图谱朝代节点
 */
export const DYNASTY_MAP = {
  D001: '新石器时代',
  D002: '夏商周',
  D003: '春秋战国',
  D004: '秦朝',
  D005: '汉朝',
  D006: '三国',
  D007: '晋朝',
  D008: '南北朝',
  D009: '隋朝',
  D010: '唐朝',
  D011: '五代十国',
  D012: '宋朝',
  D013: '辽金西夏',
  D014: '元朝',
  D015: '明朝',
  D016: '清朝',
  D017: '近代',
}

/**
 * 预约/订单状态
 */
export const ORDER_STATUS_MAP = {
  PENDING: '待审核',
  CONFIRMED: '已通过',
  REJECTED: '已驳回',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

/**
 * 非遗/体验审核状态
 */
export const APPROVAL_STATUS_MAP = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  INACTIVE: '已下架',
}

/**
 * AIGC生成类型
 */
export const AIGC_TYPE_MAP = {
  '文本': '文本',
  '图像': '图像',
}

/**
 * 通用取中文名工具
 */
export function getLabel(map, key) {
  return map[key] || key || ''
}

export function getCategoryName(code) {
  return CATEGORY_MAP[code] || code || ''
}

export function getLevelName(code) {
  return LEVEL_MAP[code] || code || ''
}

export function getExperienceTypeName(code) {
  return EXPERIENCE_TYPE_MAP[code] || code || ''
}

export function getDynastyName(code) {
  return DYNASTY_MAP[code] || code || ''
}

export function getSkillName(code) {
  return SKILL_NODE_MAP[code] || code || ''
}

export function getRoleName(code) {
  return ROLE_MAP[code] || code || ''
}

export function getOrderStatusName(code) {
  return ORDER_STATUS_MAP[code] || code || ''
}

export function getApprovalStatusName(code) {
  return APPROVAL_STATUS_MAP[code] || code || ''
}
