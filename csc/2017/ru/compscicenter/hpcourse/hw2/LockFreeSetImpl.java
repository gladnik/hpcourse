package ru.compscicenter.hpcourse.hw2;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Arrays.asList;

/**
 * @author gladnik (Nikolai Gladkov)
 */
public class LockFreeSetImpl<T extends Comparable<T>> implements ru.compscicenter.hpcourse.hw2.LockFreeSet<T> {

    private class Node {
        private T value;
        private AtomicReference<State> state = new AtomicReference<>();

        Node(final T value, final State initialState) {
            this.value = value;
            state.set(initialState);
        }

        T getValue() {
            return value;
        }

        AtomicReference<State> state() {
            return state;
        }
    }

    private class State {
        final Node next;
        private final boolean isLogicallyDeleted;

        State(final Node next, boolean isLogicallyDeleted) {
            this.next = next;
            this.isLogicallyDeleted = isLogicallyDeleted;
        }
    }

    private AtomicReference<Node> head = new AtomicReference<>();

    public LockFreeSetImpl() {
        head.set(
                new Node(null, new State(null, true))
        );
    }

    @Override
    public boolean add(T value) {
        while (true) {
            if (this.isEmpty()) {
                Node newHead = new Node(value, new State(null, false));
                AtomicReference<Node> oldHead = new AtomicReference<>(head.get());
                State oldHeadState = oldHead.get().state().get();

                final boolean isEmpty = oldHeadState.isLogicallyDeleted && oldHeadState.next == null;
                if (isEmpty && head.compareAndSet(oldHead.get(), newHead)) {
                    return true;
                }
            } else {
                List<Node> searchResult = searchExisting(value);
                Node prev = searchResult.get(0);
                Node current = searchResult.get(1);
                if (current != null && current.getValue().equals(value)) {
                    return false;
                } else {
                    Node node = new Node(value, new State(current, false));
                    AtomicReference<State> prevState = prev.state();
                    final boolean prevStateHasChanged =
                            prevState.get().isLogicallyDeleted || prevState.get().next != current;
                    if (!prevStateHasChanged && prev.state().compareAndSet(prevState.get(), new State(node, false))) {
                        return true;
                    }
                }
            }
        }
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            if (this.isEmpty()) {
                return false;
            }
            List<Node> results = searchExisting(value);
            Node prev = results.get(0);
            Node current = results.get(1);
            final boolean haveNoElement = current == null || !current.getValue().equals(value);
            if (haveNoElement) {
                return false;
            } else {
                AtomicReference<State> currentState = current.state();
                if (!currentState.get().isLogicallyDeleted) {
                    final boolean isLogicalDeletionSucceeded =
                            current.state().compareAndSet(currentState.get(), new State(currentState.get().next, true));
                    if (isLogicalDeletionSucceeded) {
                        AtomicReference<State> prevState = prev.state();
                        if (!prevState.get().isLogicallyDeleted && prevState.get().next == current) {
                            prevState.compareAndSet(prevState.get(), new State(current.state().get().next, false));
                        }
                        return true;
                    }
                }
            }
        }
    }

    @Override
    public boolean contains(T value) {
        Node current = head.get();
        while (current != null
                && current.getValue() != null
                && current.getValue().compareTo(value) < 0
                && current.state().get().next != null) {

            current = current.state().get().next;
        }
        return current != null && value.equals(current.getValue()) && !current.state().get().isLogicallyDeleted;
    }

    @Override
    public boolean isEmpty() {
        return head.get().state().get().isLogicallyDeleted;
    }


    private List<Node> searchExisting(T value) {
        searchStart:
        while (true) {
            Node prev = head.get();
            Node current = head.get().state().get().next;
            while (true) {
                if (current == null) {
                    return asList(prev, null);
                }
                AtomicReference<State> prevState = prev.state();
                AtomicReference<State> currState = current.state();
                if (prevState.get().isLogicallyDeleted || prevState.get().next != current) {
                    continue searchStart;
                }
                if (currState.get().isLogicallyDeleted) {
                    if (!current.state().compareAndSet(prevState.get(), new State(currState.get().next, false))) {
                        continue searchStart;
                    }
                    current = currState.get().next;
                } else {
                    if (current.getValue().compareTo(value) >= 0) {
                        return asList(prev, current);
                    }
                    prev = current;
                    current = currState.get().next;
                }
            }
        }
    }

}
