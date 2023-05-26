# ozon
Ozon API

## Usage

### Authentication

Create authentication manually 

```java
final OzonAuthentication auth = new OzonAuthentication(
        new OzonUserId("123456"),
        new OzonAccessToken("3.123456.ijuzFPK-S5..."),
        new OzonRefreshToken("3.123456.ijuzFPK-S5..."));
```
or import from har file

```java
final OzonAuthentication auth = OzonAuthentication.fromHar(new File("path/to/your/file.har")); 
```

### Create API client instance

```java
final Ozon ozon = new OzonBuilder()
    .authorize(Mono.just(auth), Mono.just(pinCode));
```

### Query data

```java
final ClientOperations operations = ozon.clientOperations(query).blockFirst();
        ...

final OrderList orders = ozon.orders(OrderListFilter.ALL).blockFirst();
        ...

final EChecks eChecks = ozon.eChecks().blockFirst();
        ...
```

### Download attachments

```java
final Check check = eChecks.get(0);
final URI uri = URI.create(check.button().action().link());
DataBufferUtils.write(ozon.download(uri), Path.of("output.pdf"), StandardOpenOption.CREATE).share().block();
```
