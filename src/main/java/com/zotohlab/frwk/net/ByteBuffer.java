/* Licensed under the Apache License, Version 2.0 (the "License");
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
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved. */


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

  public int readShort();

  public int readInt();

  public long readLong();

  public char readChar();

  public float readFloat();

  public double readDouble();

  ByteBuffer<T> writeByte(byte b);

  public ByteBuffer<T> writeBytes(byte[] src);

  public ByteBuffer<T> writeShort(int value);

  public ByteBuffer<T> writeInt(int value);

  public ByteBuffer<T> writeLong(long value);

  public ByteBuffer<T> writeChar(int value);

  public ByteBuffer<T> writeFloat(float value);

  public ByteBuffer<T> writeDouble(double value);

  public T getImpl();

  public byte[] toBytes();

  public void clear();

}


