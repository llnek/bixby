/*??
// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013 Ken Leung. All rights reserved.
 ??*/

package com.zotohlab.frwk.netty;

import com.zotohlab.frwk.net.MessageBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * @author kenl
 */
public class MsgBuffer implements MessageBuffer<ByteBuf> {

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
  public byte[] array() {
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
  public int readUnsignedByte() {
    return buffer.readUnsignedByte();
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
  public int readUnsignedShort() {
    return buffer.readUnsignedShort();
  }

  @Override
  public int readShort() {
    return buffer.readShort();
  }

  @Override
  public int readUnsignedMedium() {
    return buffer.readUnsignedMedium();
  }

  @Override
  public int readMedium() {
    return buffer.readMedium();
  }

  @Override
  public long readUnsignedInt() {
    return buffer.readUnsignedInt();
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
  public String readString() {
    return NettyFW.readString(buffer);
  }

  @Override
  public String[] readStrings(int numOfStrings) {
    return NettyFW.readStrings(buffer, numOfStrings);
  }

  @Override
  public MessageBuffer<ByteBuf> writeByte(byte b) {
    buffer.writeByte(b);
    return this;
  }

  @Override
  public MessageBuffer<ByteBuf> writeBytes(byte[] src) {
    buffer.writeBytes(src);
    return this;
  }

  @Override
  public MessageBuffer<ByteBuf> writeChar(int value) {
    buffer.writeChar(value);
    return this;
  }

  @Override
  public MessageBuffer<ByteBuf> writeShort(int value) {
    buffer.writeShort(value);
    return this;
  }

  @Override
  public MessageBuffer<ByteBuf> writeMedium(int value) {
    buffer.writeMedium(value);
    return this;
  }

  @Override
  public MessageBuffer<ByteBuf> writeInt(int value) {
    buffer.writeInt(value);
    return this;
  }

  @Override
  public MessageBuffer<ByteBuf> writeLong(long value) {
    buffer.writeLong(value);
    return this;
  }

  @Override
  public MessageBuffer<ByteBuf> writeFloat(float value) {
    buffer.writeFloat(value);
    return this;
  }

  @Override
  public MessageBuffer<ByteBuf> writeDouble(double value) {
    buffer.writeDouble(value);
    return this;
  }

  @Override
  public MessageBuffer<ByteBuf> writeString(String message) {
    buffer.writeBytes( NettyFW.writeString(message));
    return this;
  }

  @Override
  public MessageBuffer<ByteBuf> writeStrings(String[] messages) {
    buffer.writeBytes( NettyFW.writeStrings(messages));
    return this;
  }


}
