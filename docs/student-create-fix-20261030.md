# Student Batch Creation Fix

Release: `20261030-student-create-v4`.

## Deletion Follow-Up

Release `20261030-student-delete-v6r2` unifies deletion from student management
and user management:

- The login account is soft-deleted, role/post memberships are removed, and
  existing sessions are rejected by the account-state check.
- Student records without exam references are removed, releasing the number.
  Recreating the number creates new user and student IDs.
- Student records referenced by exam rosters, sessions, answers, code drafts
  or submissions, onsite scores/events, score revisions, incidents, files or
  result snapshots remain attached to their original user ID.
- Deleted students are hidden from the normal student list. Historical exam
  records are not erased or reassigned.
- When creating a student, an old deleted account's unused student record is
  reclaimed automatically in the creation transaction. Records with exam
  references remain reserved. No manual database cleanup is needed.
- Both deletion dialogs use one confirmation and explain the retention rule.
- The whole selected batch uses one transaction. Permission/protected-account
  failures do not leave a partially deleted batch.

`UserDeletionTest` and `CyanStudentServiceImplTest` cover service dispatch and
cleanup ordering. `deploy/windows/Verify-StudentDeletion.mjs` verifies actual
MySQL behavior and both APIs using only the restored test instance, including
fresh IDs on reuse, legacy cleanup, role/post cleanup, old-token rejection,
preserved scores and result snapshots, batch rejection, and the browser dialog.
Parallel batches with distinct fresh numbers are also checked. Cleanup skips
nonexistent student numbers to avoid unnecessary DELETE gap locks.
New student accounts receive their initial role by direct insertion, without
deleting nonexistent memberships first. Existing account role-editing behavior
is unchanged.

## Group Follow-Up

Release `20261030-student-group-v5` also fixes account, phone and email
uniqueness checks to exclude the user being edited. Editing a group no longer
rejects an unchanged login name; another user's values remain unavailable.
Both single and batch student creation now use the seeded student group
(`sys_dept.dept_id=102`, `考生分组`) instead of the question-authoring group
(`103`, `命题组`). Existing group assignments and role permissions are not
automatically rewritten. Existing students can be moved through user management.

`SysUserUniquenessTest` covers self-editing, true conflicts, new users and group
updates. The API verification script also checks both default-group paths,
repeated group edits with unchanged contact details and role preservation.

## Cause

The `STU1` and `STU2` login accounts had been soft-deleted, while their
`cyan_student` records still reserved the student numbers. The batch checked
only active login accounts. Inserting the existing student number then failed
the unique constraint and rolled back the entire batch.

## Behavior

- Check the student table directly, including historical and orphaned records.
- Skip occupied student numbers or active login names within the requested range.
- Return a successful batch response with actual created/skipped counts.
- Preserve existing identities, passwords and exam history.
- Reject conflicting single-student creation with a readable business error.
- Reject missing fields and batch sizes outside 1 to 200 before creating users.
- Disable repeated batch submission while a request is pending.
- A concurrent student-number conflict aborts and rolls back the batch with a
  readable retry message.

The count still defines the requested number range, not a promise to create
that many additional accounts. For example, prefix `STU`, start `1`, count `10`
checks `STU1` through `STU10`. Occupied numbers are skipped, not replaced or
automatically renumbered. Use a new prefix or a later starting number when
creating a new class.

## Verification

`CyanStudentServiceImplTest` covers historical numbers, mixed batches, retry,
password-reset call counts, single creation, concurrent conflicts, invalid
input and missing roles.

`deploy/windows/Verify-StudentCreate.mjs` exercises the actual API and optional
desktop browser on the restored test instance. It creates fixtures only in
the test database and verifies that repeated creation leaves passwords intact.
Set `EXAM_VERIFY_PASSWORD` before running it. Optional
`PLAYWRIGHT_MODULE_PATH` enables the browser checks.
