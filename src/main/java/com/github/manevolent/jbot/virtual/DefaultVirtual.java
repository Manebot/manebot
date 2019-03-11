package com.github.manevolent.jbot.virtual;

import com.github.manevolent.jbot.user.User;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.logging.Logger;

public class DefaultVirtual extends Virtual {
    private final Collection<VirtualProcess> processes = new LinkedList<>();

    public DefaultVirtual() {

    }

    @Override
    public Collection<VirtualProcess> getProcesses() {
        return Collections.unmodifiableCollection(processes);
    }

    @Override
    public DefaultVirtualProcess create(Runnable runnable) throws SecurityException {
        return null;
    }

    @Override
    public Thread newThread(Runnable r) {
        return create(r).thread;
    }

    private class DefaultVirtualProcess implements VirtualProcess {
        private final VirtualProcess parent;
        final Thread thread;
        private User user;

        private DefaultVirtualProcess(VirtualProcess parent, Thread thread, User user) {
            this.parent = parent;
            this.thread = thread;
            this.user = user;
        }

        @Override
        public void interrupt() {

        }

        @Override
        @Deprecated
        public void kill() throws InterruptedException {
            thread.stop();
        }

        @Override
        public long getId() {
            return thread.getId();
        }

        @Override
        public void setDescription(String s) {

        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public String getName() {
            return thread.getName();
        }

        @Override
        public boolean isRoot() {
            return getId() == 0;
        }

        @Override
        public boolean isCallerSelf() {
            return false;
        }

        @Override
        public User getUser() {
            return user;
        }

        @Override
        public void start() {
            thread.start();
        }

        @Override
        public void changeUser(User user) throws SecurityException {

        }

        @Override
        public VirtualProcess getParent() {
            return parent;
        }

        @Override
        public Logger getLogger() {
            return null;
        }

        @Override
        public boolean canControl() {
            return isCallerSelf();
        }

        @Override
        public boolean isRunning() {
            return thread.isAlive();
        }
    }
}
