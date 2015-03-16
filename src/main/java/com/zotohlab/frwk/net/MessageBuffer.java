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
// Copyright (c) 2014 Ken Leung. All rights reserved.
 ??*/

package com.zotohlab.frwk.net;



import java.nio.ByteBuffer;


/**
 * @author kenl
 * @param <T>
 */
public interface MessageBuffer<T> {

  public boolean isReadable();

  public int readableBytes();

  /**
   * Read a single signed byte from the current {@code readerIndex} position
   * of the buffer. It will increment the readerIndex after doing this
   * operation.
   *
   * @return Returns the byte that is read
   * @throws IndexOutOfBoundsException
   *             if isReadable() returns false.
   */
  public int readByte();

  public byte[] readBytes(int length);

  /**
   * Transfers this buffer's data to the specified destination starting at the
   * current {@code readerIndex} and increases the {@code readerIndex} by the
   * number of the transferred bytes (= {@code dst.length}).
   *
   * @throws IndexOutOfBoundsException
   *             if {@code dst.length} is greater than
   *             {@code this.readableBytes}
   */
  public void readBytes(byte[] dst);

  /**
   * Transfers this buffer's data to the specified destination starting at the
   * current {@code readerIndex} and increases the {@code readerIndex} by the
   * number of the transferred bytes (= {@code length}).
   *
   * @param dstIndex
   *            the first index of the destination
   * @param length
   *            the number of bytes to transfer
   *
   * @throws IndexOutOfBoundsException
   *             if the specified {@code dstIndex} is less than {@code 0}, if
   *             {@code length} is greater than {@code this.readableBytes}, or
   *             if {@code dstIndex + length} is greater than
   *             {@code dst.length}
   */
  public void readBytes(byte[] dst, int dstIndex, int length);

  /**
   * Gets an unsigned byte at the current {@code readerIndex} and increases
   * the {@code readerIndex} by {@code 1} in this buffer.
   *
   * @throws IndexOutOfBoundsException
   *             if {@code this.readableBytes} is less than {@code 1}
   */
  public int readUnsignedByte();

  /**
   * Gets a 16-bit short integer at the current {@code readerIndex} and
   * increases the {@code readerIndex} by {@code 2} in this buffer.
   *
   * @throws IndexOutOfBoundsException
   *             if {@code this.readableBytes} is less than {@code 2}
   */
  public int readShort();

  /**
   * Gets an unsigned 16-bit short integer at the current {@code readerIndex}
   * and increases the {@code readerIndex} by {@code 2} in this buffer.
   *
   * @throws IndexOutOfBoundsException
   *             if {@code this.readableBytes} is less than {@code 2}
   */
  public int readUnsignedShort();

  /**
   * Gets a 24-bit medium integer at the current {@code readerIndex} and
   * increases the {@code readerIndex} by {@code 3} in this buffer.
   *
   * @throws IndexOutOfBoundsException
   *             if {@code this.readableBytes} is less than {@code 3}
   */
  public int readMedium();

  /**
   * Gets an unsigned 24-bit medium integer at the current {@code readerIndex}
   * and increases the {@code readerIndex} by {@code 3} in this buffer.
   *
   * @throws IndexOutOfBoundsException
   *             if {@code this.readableBytes} is less than {@code 3}
   */
  public int readUnsignedMedium();

  /**
   * Gets a 32-bit integer at the current {@code readerIndex} and increases
   * the {@code readerIndex} by {@code 4} in this buffer.
   *
   * @throws IndexOutOfBoundsException
   *             if {@code this.readableBytes} is less than {@code 4}
   */
  public int readInt();

  /**
   * Gets an unsigned 32-bit integer at the current {@code readerIndex} and
   * increases the {@code readerIndex} by {@code 4} in this buffer.
   *
   * @throws IndexOutOfBoundsException
   *             if {@code this.readableBytes} is less than {@code 4}
   */
  public long readUnsignedInt();

  /**
   * Gets a 64-bit integer at the current {@code readerIndex} and increases
   * the {@code readerIndex} by {@code 8} in this buffer.
   *
   * @throws IndexOutOfBoundsException
   *             if {@code this.readableBytes} is less than {@code 8}
   */
  public long readLong();

  /**
   * Gets a 2-byte UTF-16 character at the current {@code readerIndex} and
   * increases the {@code readerIndex} by {@code 2} in this buffer.
   *
   * @throws IndexOutOfBoundsException
   *             if {@code this.readableBytes} is less than {@code 2}
   */
  public char readChar();

  /**
   * Gets a 32-bit floating point number at the current {@code readerIndex}
   * and increases the {@code readerIndex} by {@code 4} in this buffer.
   *
   * @throws IndexOutOfBoundsException
   *             if {@code this.readableBytes} is less than {@code 4}
   */
  public float readFloat();

  /**
   * Gets a 64-bit floating point number at the current {@code readerIndex}
   * and increases the {@code readerIndex} by {@code 8} in this buffer.
   *
   * @throws IndexOutOfBoundsException
   *             if {@code this.readableBytes} is less than {@code 8}
   */
  public double readDouble();

  public String readString();

  public String[] readStrings(int numOfStrings);

  MessageBuffer<T> writeByte(byte b);

  /**
   * Transfers the specified source array's data to this buffer starting at
   * the current {@code writerIndex} and increases the {@code writerIndex} by
   * the number of the transferred bytes (= {@code src.length}).
   *
   * @throws IndexOutOfBoundsException
   *             if {@code src.length} is greater than
   *             {@code this.writableBytes}
   */
  public MessageBuffer<T> writeBytes(byte[] src);

  /**
   * Sets the specified 16-bit short integer at the current
   * {@code writerIndex} and increases the {@code writerIndex} by {@code 2} in
   * this buffer. The 16 high-order bits of the specified value are ignored.
   *
   * @throws IndexOutOfBoundsException
   *             if {@code this.writableBytes} is less than {@code 2}
   */
  public MessageBuffer<T> writeShort(int value);

  /**
   * Sets the specified 24-bit medium integer at the current
   * {@code writerIndex} and increases the {@code writerIndex} by {@code 3} in
   * this buffer.
   *
   * @throws IndexOutOfBoundsException
   *             if {@code this.writableBytes} is less than {@code 3}
   */
  public MessageBuffer<T> writeMedium(int value);

  /**
   * Sets the specified 32-bit integer at the current {@code writerIndex} and
   * increases the {@code writerIndex} by {@code 4} in this buffer.
   *
   * @throws IndexOutOfBoundsException
   *             if {@code this.writableBytes} is less than {@code 4}
   */
  public MessageBuffer<T> writeInt(int value);

  /**
   * Sets the specified 64-bit long integer at the current {@code writerIndex}
   * and increases the {@code writerIndex} by {@code 8} in this buffer.
   *
   * @throws IndexOutOfBoundsException
   *             if {@code this.writableBytes} is less than {@code 8}
   */
  public MessageBuffer<T> writeLong(long value);

  /**
   * Sets the specified 2-byte UTF-16 character at the current
   * {@code writerIndex} and increases the {@code writerIndex} by {@code 2} in
   * this buffer. The 16 high-order bits of the specified value are ignored.
   *
   * @throws IndexOutOfBoundsException
   *             if {@code this.writableBytes} is less than {@code 2}
   */
  public MessageBuffer<T> writeChar(int value);

  /**
   * Sets the specified 32-bit floating point number at the current
   * {@code writerIndex} and increases the {@code writerIndex} by {@code 4} in
   * this buffer.
   *
   * @throws IndexOutOfBoundsException
   *             if {@code this.writableBytes} is less than {@code 4}
   */
  public MessageBuffer<T> writeFloat(float value);

  /**
   * Sets the specified 64-bit floating point number at the current
   * {@code writerIndex} and increases the {@code writerIndex} by {@code 8} in
   * this buffer.
   *
   * @throws IndexOutOfBoundsException
   *             if {@code this.writableBytes} is less than {@code 8}
   */
  public MessageBuffer<T> writeDouble(double value);

  public MessageBuffer<T> writeString(String message);

  public MessageBuffer<T> writeStrings(String[] message);

  /**
   * Most implementations will write an object to the underlying buffer after
   * converting the incoming object using the transformer into a byte array.
   * This method provides the flexibility to encode any type of object, to a
   * byte array or buffer(mostly).
   *
   * @param converter
   *            For most implementations, the converter which will transform
   *            the object to byte array.
   * @param <V>
   *            The object to be converted, mostly to a byte array or relevant
   *            buffer implementation.
   * @return Instance of this class itself.
   */

  /**
   * Returns the actual buffer implementation that is wrapped in this
   * MessageBuffer instance.
   *
   * @return This method will return the underlying buffer.
   */
  public T getImpl();

  /**
   * Returns the backing byte array of this buffer.
   *
   * @throws UnsupportedOperationException
   *             if there no accessible backing byte array
   */
  public byte[] array();

  /**
   * Clears the contents of this buffer.
   */
  public void clear();

}
