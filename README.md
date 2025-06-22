# 간단한 송금 서비스 구현기 with synchronized

multi-thread 환경에서 간단하게 동시성을 제어할 방법으로 가장 먼저 떠오르는 건 java의 `synchronized`다.
간단하고 직관적이긴 하지만, 실제로 사용해보면 신경써야 할 부분이 조금 있다.

이 글에서는 간단한 송금 서비스를 구현하면서, `synchronized`를 어떤 식으로 써야 하는지, 어디에 lock을 걸어야 하는지, deadlock은 어떻게 피할 수 있는지 등 다양한 시행착오 속 나의 고민과 해결책을 공유한다.

---

## synchronized?

java는 객체마다 하나의 **monitor lock**을 가진다. `synchronized`는 이 monitor lock을 이용해 임계 영역(메서드/블록)의 **thread-safe**를 보장한다.

```java
synchronized (instance) {
    // ...
}
```

즉, 한 thread가 synchronized 블록에 진입하면, 다른 thread는 대기 상태로 바뀌고 blocking된다.
lock 경쟁이 심한 상황에서는 이러한 thread-blocking이 많이 발생해 성능 병목이 될 수 있다.
그렇기 때문에 무작정 전체 메서드에 lock을 거는 게 아니라, **최소한의 lock 범위를 가져가는 것**이 중요하다.

---

## 기본 코드 구조

각 계좌는 id와 balance를 가지며, hashmap에 저장되어 있다.

```java
public class TransferUseCase {
    // ...
    public void execute(Long amount, String senderId, String receiverId) {
        var sender = repository.findById(senderId);
        var receiver = repository.findById(receiverId);

        if (sender.balance() < amount) {
            throw new RuntimeException("잔액 부족");
        }

        repository.save(senderId, sender.balance() - amount);
        repository.save(receiverId, receiver.balance() + amount);
    }
}
```

---

## 1차 테스트

1000개의 thread를 만들어, `samsung ↔ lotte` 사이에 송금을 반복하게 해봤다.
송금이 정상적으로 처리됐다면, 각 계좌의 잔액을 합쳤을 때 원래 총액(2000원)이 유지돼야 한다.

```java
long total = samsung.balance() + lotte.balance();
assertEquals(2000L, total);
```

하지만 실제로는 총액이 계속 달라진다. **race condition**이다. 잔액을 조회하고 쓰는 사이에 다른 thread가 중간에 끼어들면서 값이 덮어쓰여 버리는 것이다.

---

## 어디에 lock을 걸어야 할까?

일단 method 전체에 synchronized를 걸어보자.

```java
public synchronized void execute(...) { ... }
```

이러면 동시성 문제는 사라지지만, 모든 송금이 하나씩 처리되기에 multi-thread의 이점을 전혀 누리지 못한다.

생각해보면, 서로 무관한 송금은 동시에 처리해도 된다. 예를 들어 samsung → lotte 송금과 naver → kakao 송금은 완전히 별개의 계좌를 다룬다. 굳이 순차적으로 처리할 이유가 없다.

송금에 사용되는 계좌들만 lock을 걸면 되지 않을까?

---

## 계좌별 lock 적용

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

---

## 2차 테스트

계좌별 lock을 적용한 이후 테스트해보니 테스트가 끝나지 않는다. 왜일까?

thread들을 디버깅해보니, thread들이 모두 wait 상태에 빠져있었다.
결과적으로 thread 서로가 가진 lock을 기다리는 **circular wait**가 발생한 것이다. 전형적인 **deadlock** 상황이다.

---

## lock 획득 순서 고정 & 최종 테스트

deadlock은 여러 lock을 **일관되지 않은 순서로** 획득할 때 발생한다.
해결 방법은 간단하다. **lock을 항상 일정한 순서로 획득하면 된다.**

계좌 id를 사전순으로 정렬해서 먼저 오는 계좌에 먼저 lock을 걸고, 그 다음 계좌에 lock을 걸도록 수정했다.

```java
String first = senderId.compareTo(receiverId) < 0 ? senderId : receiverId;
String second = senderId.compareTo(receiverId) < 0 ? receiverId : senderId;

synchronized (getLock(first)) {
    synchronized (getLock(second)) {
        // 기존 송금 로직
    }
}
```

이후 테스트에 timeout 옵션을 추가하고 다시 돌려보니, timeout 발생없이 총합도 2000원으로 정확히 일치했다.

```java
boolean isSafe = latch.await(5, TimeUnit.SECONDS);
// ...
assertFalse(isSafe);
```

---

## 리팩토링?

이 시점에서 기능은 잘 동작하며, 테스트도 다 통과한다.
하지만 코드에 동시성 제어 로직와 도메인 로직이 섞여 있어서 테스트나 변경에 유연하지 못한 것 같았다.

그래서 동시성 제어 로직을 도메인 로직과 분리했다.
TransferUseCase는 순수하게 도메인 로직만 수행하고, 동시성은 TransferUseCase를 가지는 Adapter에서 처리하도록 변경했다.

```java
public final class TransferAdapter {

    private final Map<String, Object> accountLocks = new ConcurrentHashMap<>();

    private Object getLock(String id) {
        return accountLocks.computeIfAbsent(id, k -> new Object());
    }

    public void execute(Long amount, String senderId, String receiverId) {
        String first = senderId.compareTo(receiverId) < 0 ? senderId : receiverId;
        String second = senderId.compareTo(receiverId) < 0 ? receiverId : senderId;

        synchronized (getLock(first)) {
            synchronized (getLock(second)) {
                useCase.execute(amount, senderId, receiverId);
            }
        }
    }
}
```

이를 통해 동시성 제어 방법이 바뀌더라도, 다른 Adapter를 새로 만들기만 하면 되니 기존 도메인 로직을 건들일 필요가 없게 됐다.
그리고 순수 도메인 로직만을 테스트하거나 다른 동시성 제어 전략을 테스트하는 것도 더욱 유연해졌다.
