# Fabrarium development rules

- Support Paper and Folia without `BukkitScheduler`, `BukkitRunnable`, or NMS.
- Access blocks only through their owning `RegionScheduler`; access players through their `EntityScheduler`.
- Keep database and filesystem I/O away from tick threads.
- Persist every machine transition and use idempotent `operationId` values for repeatable effects.
- Keep integrations optional and discover public services at runtime.
- Add automated tests for every behavior change.
