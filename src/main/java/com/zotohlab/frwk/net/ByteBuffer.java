// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2014, Ken Leung. All rights reserved.

package com.zotohlab.frwk.net;




/**
 * @author kenl
 * @param <T>
 */
public interface ByteBuffer<T> {

  public boolean isReadable();

  public int readableBytes();

  public int readByte();

  public byte[] readBytes(int length);

  public void readBytes(byte[] dst);

  public void readBytes(byte[] dst, int dstIndex, int length);

  public int readUnsignedByte();

  public int readShort();

  public int readUnsignedShort();

  public int readInt();

  public long readUnsignedInt();

  public long readLong();

  public char readChar();

  public float readFloat();

  public double readDouble();

  public String readString();

  public String[] readStrings(int numOfStrings);

  ByteBuffer<T> writeByte(byte b);

  public ByteBuffer<T> writeBytes(byte[] src);

  public ByteBuffer<T> writeShort(int value);

  public ByteBuffer<T> writeInt(int value);

  public ByteBuffer<T> writeLong(long value);

  public ByteBuffer<T> writeChar(int value);

  public ByteBuffer<T> writeFloat(float value);

  public ByteBuffer<T> writeDouble(double value);

  public ByteBuffer<T> writeString(String message);

  public ByteBuffer<T> writeStrings(String[] message);

  public T getImpl();

  public byte[] toBytes();

  public void clear();

}
