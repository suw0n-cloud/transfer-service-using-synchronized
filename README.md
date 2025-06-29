# 간단한 송금 서비스 구현기 with java synchronized

## java의 synchronized

java는 객체마다 하나의 **monitor lock**을 가진다. `synchronized`는 이 monitor lock을 이용해 임계 영역(메서드/블록)의 **thread-safe**를 보장한다.

```java
synchronized (instance) {
    // ...
}
```

즉, 한 thread가 synchronized 블록에 진입하면, 다른 thread는 대기 상태로 바뀌고 blocking된다.
lock 경쟁이 심한 상황에서는 이러한 thread-blocking이 많이 발생해 성능 병목이 될 수 있다.
그렇기 때문에 무작정 전체 메서드에 lock을 거는 게 아니라, **최소한의 lock 범위를 가져가는 것**이 중요하다.

`synchronized`는 jvm 내의 monitor lock을 사용하기에 다중 process 환경에서는 동작하지 않는다. 이 경우에는 db lock 또는 분산락 등의 방법을 사용해야 한다. (이 글은 단일 process 환경이라 가정한다.)

---

## 기본 구조 및 테스트

각 계좌는 id와 balance를 가지며, hashmap에 저장되어 있다.

```java
public class TransferUseCase {
    // ...
    public void execute(Long amount, String senderId, String receiverId) {
        final Long balanceOfSender = repository.findById(senderId).balance();
        final Long balanceOfReceiver = repository.findById(receiverId).balance();

        if(balanceOfSender < amount) {
            throw new RuntimeException("Sender can't afford " + amount + ".");
        }

        repository.save(senderId, balanceOfSender - amount);
        repository.save(receiverId, balanceOfReceiver + amount);
    }
}
```

1000개의 thread를 만들어, `samsung ↔ lotte` 사이에 송금을 반복하게 해봤다.
송금이 정상적으로 처리됐다면, 각 계좌의 잔액을 합쳤을 때 원래 총액(2000원)이 유지돼야 한다.

```java
long total = samsung.balance() + lotte.balance();
assertEquals(2000L, total);
```

하지만 실제로는 총액이 계속 달라진다. 잔액을 조회하고 쓰는 사이에 다른 thread가 중간에 끼어들어 값을 덮어씌운 것이다. (**= race condition**)

```text
Thread A                           Thread B
---------                          ---------
read balance (1000)                read balance (1000)
↓                                  ↓
write balance (1000 - 100 = 900)   write balance (1000 - 100 = 900)
↓                                  ↓
잔액: 900 (원래는 800이어야 함.)
```

---

## 동기화

일단 method 전체에 synchronized를 걸어보자.

```java
public synchronized void execute(...) { ... }
```

이러면 동시성 문제는 사라지지만, 모든 송금이 하나씩 처리되기에 multi-thread의 이점을 전혀 누리지 못한다.

생각해보면, 서로 무관한 송금은 동시에 처리해도 된다. 예를 들어 samsung → lotte 송금과 naver → kakao 송금은 완전히 별개의 계좌를 다룬다. 굳이 순차적으로 처리할 이유가 없다.

송금에 사용되는 계좌들만 lock을 걸면 되지 않을까?

계좌 id별로 별도의 lock 객체를 만들었다. `ConcurrentHashMap<String, Object>`로 lock이 없으면 새로 만들고, 있으면 기존 걸 반환하도록 구현했다.
계좌 id별로 항상 같은 객체를 반환하므로, synchronized가 특정 계좌에만 lock을 건다.

```java
private final Map<String, Object> accountLocks = new ConcurrentHashMap<>();

private Object getLock(String id) {
    return accountLocks.computeIfAbsent(id, k -> new Object());
}
```

이제 송금 로직은 아래처럼 바뀌었다.

```java
public void execute(...) {
    synchronized (getLock(senderId)) {
        synchronized (getLock(receiverId)) {
            // 기존 송금 로직
        }
    }
}
```

하지만 테스트를 실행하니 테스트가 끝나지 않는다. 왜일까?

---

## deadlock

thread들을 디버깅해보니, thread들이 모두 wait 상태에 빠져있었다.
결과적으로 thread 서로가 가진 lock을 기다리는 **circular wait**가 발생한 것이다. 전형적인 **deadlock** 상황이다.

```text
Thread A                           Thread B
---------                          ----------
lock(samsung)                      lock(lotte)
↓                                  ↓
wait(lotte)                        wait(samsung)

→ 모든 thread가 wait 상태.
```

deadlock은 여러 lock을 **일관되지 않은 순서로** 획득할 때 발생한다.
해결 방법은 간단하다. **lock을 항상 일정한 순서로 획득하면 된다.**

계좌 id를 사전순으로 정렬해서 먼저 오는 계좌에 먼저 lock을 걸고, 그 다음 계좌에 lock을 걸도록 수정했다.

```java
String first = senderId.compareTo(receiverId) < 0 ? senderId : receiverId;
String second = senderId.compareTo(receiverId) < 0 ? receiverId : senderId;

synchronized (getLock(first)) {
    synchronized (getLock(second)) {
        //기존 송금 로직
    }
}
```

---

## 최종 결과
테스트에 timeout 옵션을 추가하고 실행해보니, 정상적으로 테스트가 종료되고 balance 총합도 2000원으로 일치했다.

```java
boolean isSafe = latch.await(5, TimeUnit.SECONDS);
// ...
assertFalse(isSafe);
```


