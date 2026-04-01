package frontReset.util;

/**
 * Simple fixed size queue helper class
 */
public class Queue<T> {

  private T[] data;
  private int head = 0; // Points to the front of the queue
  private int tail = 0; // Points to the next available slot at the end of the queue
  private int size = 0; // Tracks the number of elements in the queue

  public Queue(int max_size) {
    data = (T[]) new Object[max_size];
  }

  /**
   * Adds an item to the end of the queue.
   * @param item The item to add to the queue
   * @return Whether the item was successfully added
   */
  public boolean enqueue(T item) {
    if (size == data.length) { // Queue is full
      return false;
    }
    data[tail] = item;
    tail = (tail + 1) % data.length; // Wrap around if necessary
    size++;
    return true;
  }

  /**
   * Removes and returns the item at the front of the queue.
   * @return The item removed, or `null` if the queue is empty
   */
  public T dequeue() {
    if (size == 0) { // Queue is empty
      return null;
    }
    T item = data[head];
    head = (head + 1) % data.length; // Wrap around if necessary
    size--;
    return item;
  }

  /**
   * Peeks at the item at the front of the queue without removing it.
   * @return The item at the front, or `null` if the queue is empty
   */
  public T front() {
    return (size == 0 ? null : data[head]);
  }

  /**
   * Peeks at the item at the end of the queue without removing it.
   * @return The item at the end, or `null` if the queue is empty
   */
  public T back() {
    return (size == 0 ? null : data[(tail - 1 + data.length) % data.length]);
  }

  /**
   * Removes and returns item at the back of the queue (most recently added).
   * @return The item removed, or `null` if the queue is empty
   */
  public T popBack() {
    if (size == 0) { // Queue is empty
      return null;
    }
    tail = (tail - 1 + data.length) % data.length; // Move tail backward and wrap around
    T item = data[tail];
    size--;
    return item;
  }
  
  /**
   * Returns the total capacity of the queue
   * @return The size of the data array
   */
  public int capacity() {
    return data.length;
  }

  /**
   * Returns the number of items currently in the queue
   * @return The current size of the queue
   */
  public int used() {
    return size;
  }

  /**
   * Returns the number of available slots in the queue
   * @return The number of unused slots in the queue
   */
  public int available() {
    return data.length - size;
  }

  /**
   * Returns whether the queue is empty
   * @return Whether the queue is empty
   */
  public boolean empty() {
    return size == 0;
  }

  /**
   * Removes all items from the queue
   */
  public void clear() {
    head = 0;
    tail = 0;
    size = 0;
  }
}
