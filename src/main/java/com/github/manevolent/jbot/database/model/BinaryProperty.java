package com.github.manevolent.jbot.database.model;

import com.github.manevolent.jbot.property.Property;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public abstract class BinaryProperty implements Property {
    private final String ENCODING = "UTF-16";

    public abstract int size();
    public int write(byte[] bytes) {
        return write(bytes, 0, bytes.length);
    }
    public abstract int write(byte[] bytes, int offs, int len);
    public abstract int read(byte[] bytes, int offs, int len);

    public ByteBuffer read() {
        byte[] b = new byte[size()];
        read(b, 0, b.length);
        return ByteBuffer.wrap(b);
    }

    @Override
    public String getString() {
        try {
            if (isNull()) return null;
            return new String(read().array(), ENCODING);
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    @Override
    public boolean getBoolean() {
        if (isNull()) return false;
        return read().get() == 0x1;
    }

    @Override
    public byte[] getBytes() {
        if (isNull()) return new byte[0];
        return read().array();
    }

    @Override
    public byte getByte() {
        if (isNull()) return 0x0;
        return read().get();
    }

    @Override
    public short getShort()
    {
        if (isNull()) return 0x0;
        return read().getShort();
    }

    @Override
    public int getInteger() {
        if (isNull()) return 0x0;
        return read().getInt();
    }

    @Override
    public long getLong() {
        if (isNull()) return 0x0;
        return read().getLong();
    }

    @Override
    public float getFloat() {
        if (isNull()) return 0x0;
        return read().getFloat();
    }

    @Override
    public double getDouble() {
        if (isNull()) return 0x0;
        return read().getDouble();
    }


    @Override
    public Date getDate() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        calendar.setTimeInMillis(getInteger() * 1000);
        calendar.setTimeZone(TimeZone.getDefault());
        return calendar.getTime();
    }

    @Override
    public void set(String s) {
        try {
            set(s.getBytes(ENCODING));
        } catch (UnsupportedEncodingException e) {
        }
    }

    @Override
    public void set(boolean b) {
        write(new byte[] { b ? (byte)0x1 : (byte)0x0 }, 0, 1);
    }

    @Override
    public void set(byte b) {
        write(new byte[] { b }, 0, 1);
    }

    @Override
    public void set(short s) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(2);
        byteBuffer.putShort(s);
        set(byteBuffer.array());
    }

    @Override
    public void set(int i) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(4);
        byteBuffer.putInt(i);
        set(byteBuffer.array());
    }

    @Override
    public void set(long l) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(8);
        byteBuffer.putLong(l);
        set(byteBuffer.array());
    }

    @Override
    public void set(float f) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(4);
        byteBuffer.putFloat(f);
        set(byteBuffer.array());
    }

    @Override
    public void set(double d) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(8);
        byteBuffer.putDouble(d);
        set(byteBuffer.array());
    }

    @Override
    public void set(Date date) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTime(date);
        calendar.setTimeZone(TimeZone.getTimeZone("GMT"));
        set((int) (calendar.getTimeInMillis() / 1000L));
    }

    @Override
    public char getChar() {
        if (isNull()) return 0x0;
        return read().getChar();
    }

    @Override
    public void set(char c) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(2);
        byteBuffer.putChar(c);
        set(byteBuffer.array());
    }

    @Override
    public void set(byte[] data) {
        write(data);
    }
}
