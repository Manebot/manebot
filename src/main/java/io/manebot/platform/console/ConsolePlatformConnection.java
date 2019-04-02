package io.manebot.platform.console;

import io.manebot.DefaultBot;
import io.manebot.chat.*;
import io.manebot.platform.Platform;
import io.manebot.platform.PlatformConnection;
import io.manebot.platform.PlatformUser;
import io.manebot.user.UserRegistration;
import io.manebot.virtual.Virtual;
import io.manebot.virtual.VirtualProcess;
import org.jline.reader.*;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import java.util.function.Consumer;
import java.util.logging.Level;

public class ConsolePlatformConnection implements PlatformConnection {
    public static final String CONSOLE_UID = "stdin";
    public static final String CONSOLE_CHAT_ID = "std";

    private final DefaultBot bot;
    private final Platform platform;
    private final Chat consoleChat;
    private final PlatformUser consoleUser;

    private Terminal terminal;

    public ConsolePlatformConnection(DefaultBot bot, Platform platform) {
        this.bot = bot;
        this.platform = platform;
        this.consoleUser = new ConsoleUser();
        this.consoleChat = new ConsoleChat();
    }

    @Override
    public void disconnect() {
        try {
            terminal.close();
        } catch (IOException e) {
            Virtual.getInstance().currentProcess().getLogger().log(Level.WARNING, "Problem closing terminal", e);
        }
    }

    @Override
    public void connect() {
        try {
            terminal = TerminalBuilder.builder().streams(System.in, System.out).dumb(true).build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .variable(LineReader.SECONDARY_PROMPT_PATTERN, "%M%P > ")
                .build();

        VirtualProcess process = Virtual.getInstance().create(() -> {
            ChatSender sender = consoleUser.createSender(consoleChat);

            while (true) {
                String line;

                try {
                    line = reader.readLine(
                            Virtual.getInstance().currentProcess().getName() + "> ",
                            "",
                            (MaskingCallback) null,
                            null
                    );
                } catch (UserInterruptException e) {
                    continue;
                } catch (EndOfFileException e) {
                    return;
                }

                try {
                    bot.getChatDispatcher().execute(new BasicTextChatMessage(sender, line));
                } catch (Throwable e) {
                    Virtual.getInstance().currentProcess()
                            .getLogger().log(Level.SEVERE, "Problem executing Console command", e);
                }
            }
        });

        process.setDescription("console");
        process.start();
    }

    @Override
    public UserRegistration getUserRegistration() {
        return bot.getDefaultUserRegistration();
    }

    @Override
    public PlatformUser getSelf() {
        return consoleUser;
    }

    @Override
    public PlatformUser getPlatformUser(String s) {
        if (s.equals(CONSOLE_UID)) return consoleUser;
        else return null;
    }

    @Override
    public Collection<PlatformUser> getPlatformUsers() {
        return Collections.singleton(consoleUser);
    }

    @Override
    public Collection<String> getPlatformUserIds() {
        return Collections.singleton(CONSOLE_UID);
    }

    @Override
    public Collection<Chat> getChats() {
        return Collections.singletonList(consoleChat);
    }

    @Override
    public Collection<String> getChatIds() {
        return Collections.singletonList(CONSOLE_CHAT_ID);
    }

    private class ConsoleUser implements PlatformUser {
        @Override
        public Platform getPlatform() {
            return platform;
        }

        @Override
        public String getId() {
            return CONSOLE_UID;
        }

        @Override
        public boolean isSelf() {
            return true;
        }

        @Override
        public Collection<Chat> getChats() {
            return Collections.singletonList(consoleChat);
        }
    }

    private class ConsoleChat implements Chat {
        @Override
        public Platform getPlatform() {
            return ConsolePlatformConnection.this.platform;
        }

        @Override
        public String getId() {
            return CONSOLE_CHAT_ID;
        }

        @Override
        public void setName(String s) throws UnsupportedOperationException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isConnected() {
            return System.out != null;
        }

        @Override
        public void removeMember(String s) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addMember(String s) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<ChatMessage> getLastMessages(int i) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<PlatformUser> getPlatformUsers() {
            return Collections.singletonList(consoleUser);
        }

        @Override
        public boolean isPrivate() {
            return true;
        }

        @Override
        public ChatMessage sendMessage(Consumer<ChatMessage.Builder> consumer) {
            BasicTextChatMessage.Builder builder = new BasicTextChatMessage.Builder(consoleUser, consoleChat) {
                @Override
                public BasicTextChatMessage build() {
                    System.out.println(getMessage());
                    return super.build();
                }
            };

            consumer.accept(builder);

            return builder.build();
        }

        @Override
        public boolean canChangeTypingStatus() {
            return false;
        }

        @Override
        public void setTyping(boolean b) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isTyping() {
            return false;
        }

        @Override
        public Collection<Character> getCommandPrefixes() {
            return Collections.singletonList(null);
        }

        // This will execute all chats as a command
        @Override
        public ChatMessage parseCommand(final ChatMessage message) {
            if (message.getMessage().length() <= 0) return null;
            return message;
        }
    }
}
