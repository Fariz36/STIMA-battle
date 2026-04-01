package frontReset.util;

/**
 * Simple fixed size stack helper class
 */
public class Stack<T> {
  
  private T[] data;
  private int index = 0;
  
  public Stack(int max_size) {
    data = (T[]) new Object[max_size];
  }

  /**
   * Adds an item onto the stack.
   * @param item The item to be added to the stack
   * @return Whether the item was successfully added to the stack
   */
  public boolean push(T item) {
    if (index == data.length) { remove(0); }
    data[index++] = item;
    return true;
  }

  /**
   * Inserts an item to the given position on the stack.
   * It replaces the current item at that index and bumps the rest
   * of the stack up.
   * @param item The item to insert
   * @param i The index in the stack to insert the item
   * @return Whether the item was successfully inserted
   */
  public boolean insert(T item, int i) {
    if (i > index) { return false; }
    if (index == data.length) { remove(0); }
    for (int j = index++; j > i; --j) {
      data[j] = data[j - 1];
    }
    data[i] = item;
    return true;
  }

  /**
   * Removes an item from the stack.
   * @return The item which was removed, or `null` if empty
   */
  public T pop() {
    return (index == 0 ? null : data[--index]);
  }

  /**
   * Removes the item at the given position from the stack.
   * @return The item that was removed or null
   */
  public T remove(int i) {
    if (index == 0 || i >= index) { return null; }
    T ret = data[i];
    --index;
    for (int j = i; i < index; ++i) {
      data[j] = data[j+1];
    }
    return ret; 
  }

  /**
   * Peeks at the next item on the stack without modifying it
   * @return The top item on the stack
   */
  public T top() {
    return (index == 0 ? null : data[index - 1]);
  }

  /**
   * Returns the item at the given index
   * @param i The index to retrieve the entry at
   */
  public T peek(int i) {
    return (i < index ? data[i] : null);
  }

  /**
   * Peeks at the second item on the stack without modifying it
   * @return The second item on the stack
   */
  public T peekSecond() {
    return (index <= 1 ? null : data[index - 2]);
  }

  /**
   * Returns the item at the bottom of the stack without modification.
   * This is not a conventional stack method.
   * @return The first element in the array (bottom of the stack)
   */
  public T bottom() {
    return (index == 0 ? null : data[0]);
  }

  /**
   * Returns the total capacity of the stack
   * @return The size of the data array
   */
  public int capacity() { return data.length; }

  /**
   * Returns the usage of the stack
   * @return The number of elements filled in the array
   */
  public int used() { return index; }

  /**
   * Returns the available space in the stack
   * @return The number of unused spaces in the array
   */
  public int available() { return data.length - index; }

  /**
   * Returns whether the stack is empty
   * @return Whether the stack is empty
   */
  public boolean empty() { return index == 0; }

  /**
   * Removes all elements from the stack
   */
  public void clear() { index = 0; }

  /**
   * Checks whether a given item is in the stack
   */
  public boolean contains(T obj) {
    for (int i = 0; i < index; ++i) {
      if (data[i].equals(obj)) { return true; }
    }
    return false;
  }

}

