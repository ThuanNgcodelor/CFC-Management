export const MOVEMENT_DEFAULTS = {
  page: 0, size: 20, keyword: '', type: '', status: '', workforceGroup: '',
  departmentId: '', from: '', to: '', recordType: 'ALL', sort: 'processedAt,desc',
};

export const MOVEMENT_SORT_FIELDS = {
  employeeCode: 'Mã nhân sự', employeeName: 'Họ tên', movementType: 'Loại biến động',
  effectiveDate: 'Ngày hiệu lực', status: 'Trạng thái', processedBy: 'Người xử lý',
  processedAt: 'Thời điểm xử lý', createdAt: 'Thời điểm tạo',
};

export const MOVEMENT_ALLOWED = {
  size: [20, 50],
  type: ['', 'INITIAL_LOAD', 'INCREASE', 'DECREASE', 'TRANSFER', 'POSITION_CHANGE', 'WORKING_CONDITION_CHANGE', 'ADJUSTMENT', 'REHIRE'],
  status: ['', 'DRAFT', 'CONFIRMED', 'CANCELLED'],
  workforceGroup: ['', 'OFFICE', 'GENERAL_LABOR', 'LEGACY_UNKNOWN'],
  recordType: ['ALL', 'ORIGINAL', 'ADJUSTMENT', 'SUPERSEDED'],
  sort: Object.keys(MOVEMENT_SORT_FIELDS).flatMap((field) => [`${field},asc`, `${field},desc`]),
};

export function movementNumberParts(value = '') {
  const match = /^(\d+)(\/.*)$/.exec(value);
  return match ? { number: match[1], suffix: match[2], split: true } : { number: '', suffix: '', split: !value };
}

export function defaultMovementSuffix(year = new Date().getFullYear()) {
  return `/HĐLĐ-PBHC/${year}`;
}
