package com.github.manevolent.jbot.platform.console;

import com.github.manevolent.jbot.JBot;
import com.github.manevolent.jbot.chat.*;
import com.github.manevolent.jbot.chat.exception.ChatException;
import com.github.manevolent.jbot.command.CommandSender;
import com.github.manevolent.jbot.platform.Platform;
import com.github.manevolent.jbot.platform.PlatformConnection;
import com.github.manevolent.jbot.platform.PlatformUser;
import com.github.manevolent.jbot.virtual.Virtual;
import com.github.manevolent.jbot.virtual.VirtualProcess;
import org.jline.reader.*;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConsolePlatformConnection implements PlatformConnection {
    public static final String CONSOLE_UID = "stdin";
    public static final String CONSOLE_CHAT_ID = "std";

    private final JBot bot;
    private final Platform platform;
    private final Chat consoleChat;
    private final PlatformUser consoleUser;

    private Terminal terminal;

    public ConsolePlatformConnection(JBot bot, Platform platform) {
        this.bot = bot;
        this.platform = platform;
        this.consoleUser = new ConsoleUser();
        this.consoleChat = new ConsoleChat();
    }

    @Override
    public void connect() {
        try {
            terminal = TerminalBuilder.terminal();
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
                    bot.getChatDispatcher().execute(new ReceivedChatMessage() {
                        final Date date = Calendar.getInstance().getTime();

                        @Override
                        public ChatSender getSender() {
                            return sender;
                        }

                        @Override
                        public void delete() throws UnsupportedOperationException {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public void edit(ChatMessage chatMessage) throws UnsupportedOperationException {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public Date getDate() {
                            return date;
                        }

                        @Override
                        public String getMessage() {
                            return line;
                        }
                    });
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
    public PlatformUser getSelf() {
        return null;
    }

    @Override
    public PlatformUser getPlatformUser(String s) {
        return null;
    }

    @Override
    public Collection<PlatformUser> getPlatformUsers() {
        return null;
    }

    @Override
    public Collection<String> getPlatformUserIds() {
        return null;
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
        public Collection<ReceivedChatMessage> getLastMessages(int i) {
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
        public void sendMessage(String message) {
            System.out.println(message);
        }

        @Override
        public void sendMessage(RichChatMessage richChatMessage) throws UnsupportedOperationException {
            sendMessage(richChatMessage.getMessage());
        }

        @Override
        public void setTitle(String s) throws UnsupportedOperationException {
            throw new UnsupportedOperationException();
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
        public ReceivedChatMessage parseCommand(final ReceivedChatMessage message) {
            if (message.getMessage().length() <= 0) return null;
            return message;
        }
    }
}
