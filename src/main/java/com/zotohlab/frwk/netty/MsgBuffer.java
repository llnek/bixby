/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.
*/


package com.zotohlab.frwk.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import com.zotohlab.frwk.net.ByteBuffer;

/**
 * @author kenl
 */
public class MsgBuffer implements ByteBuffer<ByteBuf> {

  private final ByteBuf buffer;

  public MsgBuffer() {
    buffer = Unpooled.buffer();
  }

  public MsgBuffer(ByteBuf buffer) {
    this.buffer = buffer;
  }

  @Override
  public boolean isReadable() {
    return buffer.isReadable();
  }

  @Override
  public int readableBytes() {
    return buffer.readableBytes();
  }

  @Override
  public byte[] toBytes() {
    return buffer.array();
  }

  @Override
  public void clear() {
    buffer.clear();
  }

  @Override
  public ByteBuf getImpl() {
    return buffer;
  }

  @Override
  public int readByte() {
    return buffer.readByte();
  }

  @Override
  public byte[] readBytes(int length) {
    byte[] bytes = new byte[length];
    buffer.readBytes(bytes);
    return bytes;
  }

  @Override
  public void readBytes(byte[] dst) {
    buffer.readBytes(dst);
  }

  @Override
  public void readBytes(byte[] dst, int dstIndex, int length) {
    buffer.readBytes(dst, dstIndex, length);
  }

  @Override
  public char readChar() {
    return buffer.readChar();
  }

  @Override
  public int readShort() {
    return buffer.readShort();
  }

  @Override
  public int readInt() {
    return buffer.readInt();
  }

  @Override
  public long readLong() {
    return buffer.readLong();
  }

  @Override
  public float readFloat() {
    return buffer.readFloat();
  }

  @Override
  public double readDouble() {
    return buffer.readChar();
  }


  @Override
  public ByteBuffer<ByteBuf> writeByte(byte b) {
    buffer.writeByte(b);
    return this;
  }

  @Override
  public ByteBuffer<ByteBuf> writeBytes(byte[] src) {
    buffer.writeBytes(src);
    return this;
  }

  @Override
  public ByteBuffer<ByteBuf> writeChar(int value) {
    buffer.writeChar(value);
    return this;
  }

  @Override
  public ByteBuffer<ByteBuf> writeShort(int value) {
    buffer.writeShort(value);
    return this;
  }

  @Override
  public ByteBuffer<ByteBuf> writeInt(int value) {
    buffer.writeInt(value);
    return this;
  }

  @Override
  public ByteBuffer<ByteBuf> writeLong(long value) {
    buffer.writeLong(value);
    return this;
  }

  @Override
  public ByteBuffer<ByteBuf> writeFloat(float value) {
    buffer.writeFloat(value);
    return this;
  }

  @Override
  public ByteBuffer<ByteBuf> writeDouble(double value) {
    buffer.writeDouble(value);
    return this;
  }


}
