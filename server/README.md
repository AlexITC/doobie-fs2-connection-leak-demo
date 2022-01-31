# doobie <> fs2 PoC

This repo show cases a problem found when doobie uses fs2 to stream database items. In short, when a db stream is combined with another stream, the db connection won't return to the pool until the whole stream finishes, which is particularly problematic when using a stream that lasts for the whole application lifecycle, ended up in connection leaks.

Example:

```scala
def f(a: fs2.Stream[cats.effect.IO, String], b: fs2.Stream[cats.effect.IO, String]): Unit =
  (a ++ b).evalMap(x => IO(println)).compile.drain.unsafeRunSync()
```

When `a` is a stream from the database, we get a connection leak until `b` finishes.


```scala
def f(a: fs2.Stream[cats.effect.IO, String], b: fs2.Stream[cats.effect.IO, String]): Unit =
  (a.onFinalize(cats.effect.IO.unit) ++ b).map(println).compile.drain.unsafeRunSync()
```

When the items from `a` are consumed, the connection is returned to the pool.

## Try it

To demonstrate the behavior, run `sbt test` which show cases the correct behavior.

In the first try, `Pool stats` starts with `active=1` and becomes `active=0` once the db stream is consumed:

```
c.z.h.HikariDataSource - HikariPool-1 - Start completed.
c.z.h.p.HikariPool - HikariPool-1 - Pool stats (total=1, active=1, idle=0, waiting=0)
c.z.h.p.HikariPool - HikariPool-1 - Fill pool skipped, pool is at sufficient level.
s.SampleSpec - Db connection returned to the pool
s.SampleSpec - Step: 1
c.z.h.p.PoolBase - HikariPool-1 - Closing connection org.postgresql.jdbc.PgConnection@79a4d880: (connection has passed maxLifetime)
c.z.h.p.HikariPool - HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@30e7b49a
c.z.h.p.HikariPool - HikariPool-1 - Pool stats (total=1, active=0, idle=1, waiting=0)
c.z.h.p.HikariPool - HikariPool-1 - Fill pool skipped, pool is at sufficient level.
s.SampleSpec - Step: 2
c.z.h.p.PoolBase - HikariPool-1 - Closing connection org.postgresql.jdbc.PgConnection@30e7b49a: (connection has passed maxLifetime)
c.z.h.p.HikariPool - HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@9139dab
c.z.h.p.HikariPool - HikariPool-1 - Pool stats (total=1, active=0, idle=1, waiting=0)
```

In the second try, `active=1` remains until the whole stream is consumed:

```
c.z.h.HikariDataSource - HikariPool-2 - Start completed.
s.SampleSpec - Step: 1
c.z.h.p.HikariPool - HikariPool-2 - Pool stats (total=1, active=1, idle=0, waiting=0)
c.z.h.p.HikariPool - HikariPool-2 - Fill pool skipped, pool is at sufficient level.
c.z.h.p.ProxyLeakTask - Connection leak detection triggered for org.postgresql.jdbc.PgConnection@9bfd722 on thread pool-4-thread-1, stack trace follows
s.SampleSpec - Step: 2
c.z.h.p.HikariPool - HikariPool-2 - Pool stats (total=1, active=1, idle=0, waiting=0)
c.z.h.p.HikariPool - HikariPool-2 - Fill pool skipped, pool is at sufficient level.
c.z.h.p.ProxyLeakTask - Previously reported leaked connection org.postgresql.jdbc.PgConnection@9bfd722 on thread pool-4-thread-1 was returned to the pool (unleaked)
c.z.h.HikariDataSource - HikariPool-2 - Shutdown initiated...
c.z.h.p.HikariPool - HikariPool-2 - Before shutdown stats (total=1, active=0, idle=1, waiting=0)
c.z.h.p.PoolBase - HikariPool-2 - Closing connection org.postgresql.jdbc.PgConnection@9bfd722: (connection evicted)
c.z.h.p.HikariPool - HikariPool-2 - After shutdown stats (total=0, active=0, idle=0, waiting=0)
c.z.h.HikariDataSource - HikariPool-2 - Shutdown completed.
```
