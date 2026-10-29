# Theory Auto Grading V1

This release uses the existing `exam_grading.sql` schema; no additional migration.

- The existing 30-second expiry worker seals expired sessions, then grades submitted
  sessions in batches of 100. Manual submissions use the same durable database
  scan. Restarting the application does not lose pending work.
- Only published question snapshots determine grades. Single choice and judgment
  compare option labels. Historic judgment text is mapped to a unique matching
  snapshot option's content; ambiguous text requires manual review.
  Multiple choice compares complete sets, ignoring order
  and surrounding whitespace. Partial, extra, duplicate or unknown labels score 0.
- Unanswered snapshot questions receive empty answer records and zero scores.
  Nonempty fill-blank and short answers still require manual grading.
- Invalid objective reference data aborts that session's grading transaction and
  is logged for review, not treated as a wrong answer. Other sessions continue.
- Session locks serialize automatic grading, answer saving and manual grading.
  Already graded answers, including teacher-assigned zero, are never overwritten.
- Every automatic grade has a revision with `grader_id = 0` (reserved system actor)
  and reason `系统自动判分 v1`. Actual teacher IDs are unchanged.
- Teacher progress includes answered count (nonblank only), pending grades,
  scored subtotal and maximum score. This is NOT a published final result.
- The frontend no longer guesses grades or silently initializes ungraded scores
  to zero. Teachers must enter a grade for subjective answers.

Not included: configurable fill-blank tolerance, Python execution/testing,
review approvals, result versioning/locking, publication, ranking and export.

Deployment: back up the database before enabling this worker because it also
processes historic submitted sessions. Deploy a uniquely named JAR outside the
build directory; never overwrite a running JAR. Rolling back the executable does
not remove grades already produced. Preserve revisions and use reviewed corrections
instead of bulk deletion. Test against a restored database before production use.

## Local Verification (2026-10-29)

- 54 backend tests passed, including a real MySQL test on an isolated restored
  database. Fixtures were rolled back and the verification database removed.
- 11 frontend tests passed (answer saving, submission, legacy judgment answers,
  grading fields, correction reasons and partial-score display).
- Independent backend package: `cyan-exam-admin/target/cyan-exam-admin-autograding.jar`.
- Database backup: `D:/app/mysql-backups/cyan-exam-before-autograding-20261029.sql`.
- The running port-8080 backend was NOT replaced: exam 2 still had an active
  session. Deploy the new backend in an approved maintenance window.
