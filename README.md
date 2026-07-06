# Stock Order Matching Engine

A mini stock exchange built with Java + Spring Boot, demonstrating a
price-time priority matching engine backed by heaps.

## DSA at the core

| Structure | Where | Why |
|---|---|---|
| **Max-Heap** (`PriorityQueue`) | `buyHeap` in `OrderBook` | Highest buy price should match first |
| **Min-Heap** (`PriorityQueue`) | `sellHeap` in `OrderBook` | Lowest sell price should match first |
| **HashMap** | `openOrdersById` | O(1) lookup for cancellation |
| Custom `Comparator` | price desc/asc, then `sequence` asc | Enforces price-time (FIFO) priority |

Matching rule: an incoming BUY matches while `best resting sell price <= buy price`.
An incoming SELL matches while `best resting buy price >= sell price`. The
**resting** order's price is used as the execution price (standard
price-time priority convention) — the order that was already in the book
doesn't get worse execution than what it quoted.

## Project structure

```
src/main/java/com/matchingengine/
├── model/          Order, Trade, OrderSide, OrderStatus (JPA entities)
├── engine/          OrderBook (heap matching logic), MatchingEngine (per-symbol routing + locking)
├── service/         OrderService (persistence + engine orchestration)
├── controller/       OrderController (REST API)
├── dto/             Request/response objects
├── repository/       Spring Data JPA repositories
└── exception/        Custom exceptions + global error handler
```

## Running it

Requires Java 17+ and Maven.

```bash
mvn spring-boot:run
```

The app boots on `http://localhost:8080` using an **in-memory H2 database**
by default — no MySQL setup needed to see it working. H2 console (optional,
for peeking at tables) is at `http://localhost:8080/h2-console`
(JDBC URL: `jdbc:h2:mem:matchingengine`, user `sa`, no password).

### Switching to MySQL

1. `CREATE DATABASE matching_engine;`
2. In `src/main/resources/application.properties`, comment out the H2 block
   and uncomment the MySQL block (fill in your password).
3. `mvn spring-boot:run` again.

### Running the tests

```bash
mvn test
```

`OrderBookTest` proves: exact-price matching, partial fills, no-match when
prices don't cross, price priority, time priority (FIFO at same price),
multi-level sweeps, and cancellation.

## API examples

**Place a sell order (rests in the book, nothing to match yet):**
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"symbol":"TCS","side":"SELL","price":490,"quantity":100}'
```

**Place a matching buy order (executes immediately):**
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"symbol":"TCS","side":"BUY","price":500,"quantity":100}'
```
Response includes the generated `trades` array showing the execution.

**View the live order book (aggregated price levels):**
```bash
curl http://localhost:8080/api/orderbook/TCS
```

**View trade history:**
```bash
curl http://localhost:8080/api/trades/TCS
```

**Get a specific order's status:**
```bash
curl http://localhost:8080/api/orders/1
```

**Cancel a resting order:**
```bash
curl -X DELETE http://localhost:8080/api/orders/TCS/1
```

## Known limitations (intentionally left for you to extend)

- `PriorityQueue.remove(Object)` is O(n), not O(log n) — noted in
  `OrderBook.cancel()`. A production book would use a `TreeMap<price,
  Deque<Order>>` for true O(log n) cancel. Good talking point / stretch goal.
- Only LIMIT orders currently (no MARKET orders) — straightforward to add.
- No self-trade prevention (a user could match against their own resting
  order) — add a check comparing trader IDs once you introduce a `User`
  concept.
- Concurrency is locked per-symbol, so two orders on the same symbol can't
  process in parallel — this is correct/required behavior, not a bug, but
  worth being able to explain in an interview.

## Suggested next steps (in order)

1. Run it, place a few orders via curl or Postman, watch trades appear.
2. Read `OrderBook.java` top to bottom — it's the entire DSA story of this project.
3. Add a `traderId` field to `Order` so you can tell whose order matched whose.
4. Add MARKET order support (matches at any price, doesn't rest if unfilled).
5. Add a WebSocket endpoint that pushes live order book updates.
6. Swap in MySQL and add a simple HTML/React dashboard showing the book depth.
