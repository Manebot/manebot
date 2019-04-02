package io.manebot.plugin.java.classloader;

import java.util.*;

public final class Utils {
  /**
   * Concatenates the content of two enumerations into one.
   * Until the
   * end of <code>en1</code> is reached its elements are being served.
   * As soon as the <code>en1</code> has no more elements, the content
   * of <code>en2</code> is being returned.
   *
   * @param ens enumerations
   * @return enumeration
   */
  public static <T> Enumeration<T> concat(Enumeration<? extends T>... ens) {
      ArrayList<Enumeration<? extends T>> two = new ArrayList<Enumeration<? extends T>>();
      two.addAll(Arrays.asList(ens));
      return new SeqEn<T>(Collections.enumeration(two));
  }

}

final class SeqEn<T> extends Object implements Enumeration<T> {
  /** enumeration of Enumerations */
  private Enumeration<? extends Enumeration<? extends T>> en;

  /** current enumeration */
  private Enumeration<? extends T> current;

  /** is {@link #current} up-to-date and has more elements?
  * The combination <CODE>current == null</CODE> and
  * <CODE>checked == true means there are no more elements
  * in this enumeration.
  */
  private boolean checked = false;

  /** Constructs new enumeration from already existing. The elements
  * of <CODE>en</CODE> should be also enumerations. The resulting
  * enumeration contains elements of such enumerations.
  *
  * @param en enumeration of Enumerations that should be sequenced
  */
  public SeqEn(Enumeration<? extends Enumeration <? extends T>> en) {
      this.en = en;
  }

  /** Ensures that current enumeration is set. If there aren't more
  * elements in the Enumerations, sets the field <CODE>current</CODE> to null.
  */
  private void ensureCurrent() {
      while ((current == null) || !current.hasMoreElements()) {
          if (en.hasMoreElements()) {
              current = en.nextElement();
          } else {
              // no next valid enumeration
              current = null;

              return;
          }
      }
  }

  /** @return true if we have more elements */
  public boolean hasMoreElements() {
      if (!checked) {
          ensureCurrent();
          checked = true;
      }

      return current != null;
  }

  /** @return next element
  * @exception NoSuchElementException if there is no next element
  */
  public T nextElement() {
      if (!checked) {
          ensureCurrent();
      }

      if (current != null) {
          checked = false;

          return current.nextElement();
      } else {
          checked = true;
          throw new NoSuchElementException();
      }
  }
}