package cp1.tests;

import cp1.base.*;
import cp1.solution.TransactionManagerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Przeplot {
    private final static long BASE_WAIT_TIME = 25;

    public static void main(String[] args) {
        Long time1 = System.currentTimeMillis();
        // Set up resources.
        ResourceImpl r1 = new ResourceImpl(ResourceIdImpl.generate());
        ResourceImpl r2 = new ResourceImpl(ResourceIdImpl.generate());
        List<Resource> resources =
                Collections.unmodifiableList(
                        Arrays.asList(r1, r2)
                );


        // Set up a transaction manager.
        TransactionManager tm =
                TransactionManagerFactory.newTM(
                        resources,
                        new LocalTimeProviderImpl()
                );


        // Set up threads operation on the resources.
        ArrayList<Thread> threads = new ArrayList<Thread>();

        threads.add(new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 3; i++ ){
                    try {
                        Thread.sleep(2 * BASE_WAIT_TIME);
                    } catch (InterruptedException e) {
                        throw new AssertionError(e);
                    }
                    try {
                        tm.startTransaction();
                    } catch (AnotherTransactionActiveException e) {
                        throw new AssertionError(e);
                    }
                    try {
                        tm.operateOnResourceInCurrentTransaction(
                                resources.get(i % 2).getId(),
                                ResourceOpImpl.get()
                        );
                        Thread.sleep(3 * BASE_WAIT_TIME);
                        tm.commitCurrentTransaction();
                        if (tm.isTransactionActive()) {
                            throw new AssertionError("Failed to commit a transaction");
                        }
                    } catch (InterruptedException |
                            ActiveTransactionAborted |
                            NoActiveTransactionException |
                            ResourceOperationException |
                            UnknownResourceIdException e) {
                        throw new AssertionError(e);
                    } finally {
                        tm.rollbackCurrentTransaction();
                    }
                }
            }
        }));
        threads.add(new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 3; i++ ){
                    try {
                        Thread.sleep(2 * BASE_WAIT_TIME);
                    } catch (InterruptedException e) {
                        throw new AssertionError(e);
                    }
                    try {
                        tm.startTransaction();
                    } catch (AnotherTransactionActiveException e) {
                        throw new AssertionError(e);
                    }
                    try {
                        tm.operateOnResourceInCurrentTransaction(
                                resources.get((i + 1) % 2).getId(),
                                ResourceOpImpl.get()
                        );
                        Thread.sleep(1 * BASE_WAIT_TIME);
                        tm.commitCurrentTransaction();
                        if (tm.isTransactionActive()) {
                            throw new AssertionError("Failed to commit a transaction");
                        }
                    } catch (InterruptedException |
                            ActiveTransactionAborted |
                            NoActiveTransactionException |
                            ResourceOperationException |
                            UnknownResourceIdException e) {
                        throw new AssertionError(e);
                    } finally {
                        tm.rollbackCurrentTransaction();
                    }
                }
            }
        }));

        // Start the threads and wait for them to finish.
        for (Thread t : threads) {
            t.start();
        }
        try {
            for (Thread t : threads) {
                t.join(10 * BASE_WAIT_TIME);
            }
        } catch (InterruptedException e) {
            throw new AssertionError("The main thread has been interrupted");
        }

        tm.print();
    }

    private final static void expectResourceValue(ResourceImpl r, long val) {
        if (r.getValue() != val) {
            throw new AssertionError(
                    "For resource " + r.getId() +
                            ", expected value " + val +
                            ", but got value " + r.getValue()
            );
        }
    }


    // ---------------------------------------------------------
    // -                                                       -
    // -     Sample implementations of the base interfaces     -
    // -                                                       -
    // ---------------------------------------------------------

    private static final class LocalTimeProviderImpl implements LocalTimeProvider {
        @Override
        public long getTime() {
            return System.currentTimeMillis();
        }
    }

    private static final class ResourceIdImpl implements ResourceId {
        private static volatile int next = 0;
        private final int value;

        private ResourceIdImpl(int value) {
            this.value = value;
        }

        public static synchronized ResourceId generate() {
            return new ResourceIdImpl(next++);
        }

        @Override
        public int compareTo(ResourceId other) {
            if (!(other instanceof ResourceIdImpl)) {
                throw new RuntimeException("Comparing incompatible resource IDs");
            }
            ResourceIdImpl second = (ResourceIdImpl) other;
            return Integer.compare(this.value, second.value);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ResourceIdImpl)) {
                return false;
            }
            ResourceIdImpl second = (ResourceIdImpl) obj;
            return this.value == second.value;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(this.value);
        }

        @Override
        public String toString() {
            return "R" + this.value;
        }
    }

    private static final class ResourceImpl extends Resource {
        private volatile long value = 0;

        public ResourceImpl(ResourceId id) {
            super(id);
        }

        public void incValue() {
            long x = this.value;
            ++x;
            this.value = x;
        }

        public void decValue() {
            long x = this.value;
            --x;
            this.value = x;
        }

        public long getValue() {
            return this.value;
        }
    }

    private static final class ResourceOpImpl extends ResourceOperation {
        private final static ResourceOpImpl singleton = new ResourceOpImpl();

        private ResourceOpImpl() {
        }

        public static ResourceOperation get() {
            return singleton;
        }

        @Override
        public String toString() {
            return "OP_" + super.toString();
        }

        @Override
        public void execute(Resource r) {
            System.out.println("OPERACJA " + r.getId());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (!(r instanceof ResourceImpl)) {
                throw new AssertionError("Unexpected resource type " +
                        r.getClass().getCanonicalName());
            }
            ((ResourceImpl) r).incValue();
            System.out.println("KONIEC OPERACJI " + r.getId());
        }

        @Override
        public void undo(Resource r) {
            if (!(r instanceof ResourceImpl)) {
                throw new AssertionError("Unexpected resource type " +
                        r.getClass().getCanonicalName());
            }
            ((ResourceImpl) r).decValue();
        }
    }
}
