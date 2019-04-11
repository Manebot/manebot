package io.manebot.virtual;

import io.manebot.security.Permission;
import io.manebot.user.User;

import java.util.*;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DefaultVirtual extends Virtual {
    private final Collection<VirtualProcess> processes = new HashSet<>();
    private final Map<Thread, VirtualProcess> threadMap = new LinkedHashMap<>();
    private final VirtualProcess root;
    private final Logger logger;

    public DefaultVirtual(User currentUser) {
        this.logger = Logger.getLogger("Virtual");

        logger.setParent(Logger.getGlobal());
        logger.setUseParentHandlers(true);

        this.root = registerProcess(new DefaultVirtualProcess(null, currentUser));
    }

    private DefaultVirtualProcess registerProcess(DefaultVirtualProcess process) {
        processes.add(process);
        threadMap.put(process.thread, process);

        return process;
    }

    private DefaultVirtualProcess unregisterProcess(DefaultVirtualProcess process) {
        processes.remove(process);
        threadMap.remove(process.thread, process);

        return process;
    }

    public VirtualProcess getRoot() {
        return root;
    }

    @Override
    public VirtualProcess getProcess(Thread thread) {
        return threadMap.get(thread);
    }

    @Override
    public User currentUser() {
        VirtualProcess process = currentProcess();
        if (process == null) return null;
        else return process.getUser();
    }

    @Override
    public Collection<VirtualProcess> getProcesses() {
        return Collections.unmodifiableCollection(processes);
    }

    @Override
    public DefaultVirtualProcess create(Runnable runnable) throws SecurityException {
        Thread currentThread = Thread.currentThread();
        VirtualProcess currentProcess = getProcess(currentThread);
        return create(currentProcess, runnable);
    }

    private DefaultVirtualProcess create(VirtualProcess parent, Runnable runnable) {
        return registerProcess(new DefaultVirtualProcess(
                parent,
                new Thread(runnable),
                parent == null ? null : parent.getUser()
        ));
    }

    @Override
    public Thread newThread(Runnable r) {
        return create(r).thread;
    }

    private class DefaultVirtualProcess implements VirtualProcess {
        private final VirtualProcess parent;
        private final Thread thread;
        private final Profiler profiler;
        private final Logger logger;

        private String description;
        private User user;

        private DefaultVirtualProcess(VirtualProcess parent, Runnable runnable, User user) {
            this.parent = parent;
            this.thread = new Thread(new Execution(this, runnable));
            this.profiler = new Profiler("root", null);
            this.user = user;
            this.description = thread.getName();

            String loggerName = thread.getName();
            this.logger = Logger.getLogger(loggerName);
            this.logger.setParent(DefaultVirtual.this.logger);

            updateName();
        }

        private DefaultVirtualProcess(VirtualProcess parent, Thread thread, User user) {
            this.parent = parent;
            this.thread = thread;
            this.profiler = new Profiler("root", null);
            this.user = user;
            this.description = thread.getName();

            String loggerName = thread.getName();
            this.logger = Logger.getLogger(loggerName);
            this.logger.setParent(DefaultVirtual.this.logger);
        }

        private void updateName() {
            thread.setName(getName());
        }

        private DefaultVirtualProcess(VirtualProcess parent, User user) {
            this(parent, Thread.currentThread(), user);
        }

        @Override
        public void interrupt() {
            if (!canControl()) throw new SecurityException("cannot control");

            thread.interrupt();
        }

        @Override
        @Deprecated
        public void kill() {
            if (!canControl()) throw new SecurityException("cannot control");

            thread.stop();
        }

        @Override
        public long getId() {
            return thread.getId();
        }

        @Override
        public void setDescription(String s) {
            if (!canControl()) throw new SecurityException("cannot control");

            this.description = s;

            updateName();
        }

        @Override
        public String getDescription() {
            return description;
        }

        public String getName() {
            return getId() + "/" + (getUser() != null ? getUser().getName() : "?") + " " + description;
        }

        @Override
        public boolean isRoot() {
            return this == root;
        }

        @Override
        public boolean isCallerSelf() {
            return Thread.currentThread() == thread;
        }

        @Override
        public User getUser() {
            return user;
        }

        @Override
        public void start() {
            if (!canControl()) throw new SecurityException("cannot control");

            thread.start();
        }

        @Override
        public void changeUser(User user) throws SecurityException {
            if (this.user == user) return;
            if (user == null) throw new IllegalArgumentException("user", new NullPointerException());

            Permission.checkPermission("system.process.changeuser");

            this.user = user;
            updateName();
        }

        @Override
        public VirtualProcess getParent() {
            return parent;
        }

        @Override
        public Logger getLogger() {
            return logger;
        }

        @Override
        public boolean canControl() {
            if (isCallerSelf()) return true; // self-check

            VirtualProcess current = currentProcess();

            VirtualProcess owner = this;
            while((owner = owner.getParent()) != null) if (owner == current) return true; // parent check

            return current != null &&
                    current.getUser() != null &&
                    current.getUser().hasPermission("system.process.controlany"); // not a direct parent
        }

        @Override
        public boolean isRunning() {
            return thread.isAlive();
        }

        @Override
        public Profiler getProfiler() {
            return profiler;
        }

        @Override
        public ThreadFactory newThreadFactory() throws SecurityException {
            if (!isCallerSelf()) throw new SecurityException("caller is not self");
            return r -> create(DefaultVirtualProcess.this, r).thread;
        }
    }

    private final class Execution implements Runnable {
        private final Runnable runnable;
        private final DefaultVirtualProcess process;

        Execution(DefaultVirtualProcess process, Runnable runnable) {
            this.process = process;
            this.runnable = runnable;
        }

        @Override
        public void run() {
            try (Profiler profiler = Profiler.region("root")) {
                // Startup/initialization
                registerProcess(process);

                // Main sequence run:
                runnable.run();
            } catch (ThreadDeath e) {
                // Ignore (don't log)
            } catch (Throwable e) {
                // Log process error.
                process.getLogger().log(Level.SEVERE, "Problem in process " + process.getName(), e);
            } finally {
                // Tear-down
                unregisterProcess(process);
            }
        }
    }
}
