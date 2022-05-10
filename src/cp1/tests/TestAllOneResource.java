package cp1.tests;

import cp1.base.*;
import cp1.solution.TransactionManagerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TestAllOneResource {
    private final static long BASE_WAIT_TIME = 500;

    public static void main(String[] args) {
        Long time1 = System.currentTimeMillis();
        // Set up resources.
        ResourceImpl r1 = new ResourceImpl(ResourceIdImpl.generate());
        List<Resource> resources =
                Collections.unmodifiableList(
                        Arrays.asList(r1)
                );


        // Set up a transaction manager.
        TransactionManager tm =
                TransactionManagerFactory.newTM(
                        resources,
                        new LocalTimeProviderImpl()
                );


        // Set up threads operation on the resources.
        ArrayList<Thread> threads = new ArrayList<Thread>();

        Runnable mRunnable = (new Runnable() {
            @Override
            public void run() {
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
                if (!tm.isTransactionActive()) {
                    throw new AssertionError("Failed to start a transaction");
                }
                if (tm.isTransactionAborted()) {
                    throw new AssertionError("Invalid transaction state");
                }
                try {
                    tm.operateOnResourceInCurrentTransaction(
                            r1.getId(),
                            ResourceOpImpl.get()
                    );
                    Thread.sleep(1 * BASE_WAIT_TIME);
                    tm.operateOnResourceInCurrentTransaction(
                            r1.getId(),
                            ResourceOpImpl.get()
                    );
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
        });
        threads.add(new Thread(mRunnable));
        threads.add(new Thread(mRunnable));
        threads.add(new Thread(mRunnable));

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
            return new TestAllOneResource.ResourceIdImpl(next++);
        }

        @Override
        public int compareTo(ResourceId other) {
            if (!(other instanceof TestAllOneResource.ResourceIdImpl)) {
                throw new RuntimeException("Comparing incompatible resource IDs");
            }
            TestAllOneResource.ResourceIdImpl second = (TestAllOneResource.ResourceIdImpl) other;
            return Integer.compare(this.value, second.value);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof TestAllOneResource.ResourceIdImpl)) {
                return false;
            }
            TestAllOneResource.ResourceIdImpl second = (TestAllOneResource.ResourceIdImpl) obj;
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
        private final static TestAllOneResource.ResourceOpImpl singleton = new TestAllOneResource.ResourceOpImpl();

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
            if (!(r instanceof TestAllOneResource.ResourceImpl)) {
                throw new AssertionError("Unexpected resource type " +
                        r.getClass().getCanonicalName());
            }
            ((TestAllOneResource.ResourceImpl) r).incValue();
        }

        @Override
        public void undo(Resource r) {
            if (!(r instanceof TestAllOneResource.ResourceImpl)) {
                throw new AssertionError("Unexpected resource type " +
                        r.getClass().getCanonicalName());
            }
            ((TestAllOneResource.ResourceImpl) r).decValue();
        }
    }
}
