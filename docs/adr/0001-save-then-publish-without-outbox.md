# Save then publish, without an outbox (for now)

Recording a Patient Move inserts the row and then publishes PatientMoved as two separate steps; if the publish fails the move stays saved, no event goes out, and the caller gets a 500. We accept this known dual-write gap on purpose: this is a learning project, and the gap is kept reproducible so it can later be fixed with a transactional outbox as a deliberate lesson. Publishing inside the DB transaction was rejected because it only swaps the failure for its mirror image (event sent, commit fails).
