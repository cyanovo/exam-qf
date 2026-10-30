export function isStudentOnly(user) {
  const permissions = user.permissions || []
  const hasStaffPermission = permissions.some(permission =>
    permission === '*:*:*' || /^(system:|monitor:|question:|exam:(exam|grading|onsite|results):)/.test(permission))
  return user.roles.includes('student') && !hasStaffPermission
}
