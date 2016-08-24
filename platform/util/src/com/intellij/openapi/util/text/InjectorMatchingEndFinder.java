/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util.text;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.lang.Math.min;

/**
 * @author Irina.Chernushina on 12/2/2015.
 */
public class InjectorMatchingEndFinder {
  public static int findMatchingEnd(@NotNull String startSymbol, @NotNull String endSymbol, @NotNull String text, int afterStartIdx) {
    if (afterStartIdx < 0) return -1;

    final Character shortStartSymbol = defineShortSymbol(startSymbol);
    final Character shortEndSymbol = defineShortSymbol(endSymbol);

    if (shortStartSymbol != null || shortEndSymbol != null) {
      return findForShortSymbols(nonNullifyShortSymbol(shortStartSymbol, startSymbol),
                                 nonNullifyShortSymbol(shortEndSymbol, endSymbol),
                                 text, afterStartIdx, endSymbol);
    }
    return findForLongSymbols(text, afterStartIdx, startSymbol, endSymbol);
  }

  private static char nonNullifyShortSymbol(@Nullable Character shortStartSymbol, @NotNull String startSymbol) {
    return shortStartSymbol == null ? startSymbol.charAt(0) : shortStartSymbol;
  }

  private static Character defineShortSymbol(final String s) {
    if (s.length() == 2 && s.charAt(0) == s.charAt(1)) return s.charAt(0);
    return null;
  }

  private static int findForShortSymbols(char shortStartSymbol, char shortEndSymbol, @NotNull final String text,
                                                          int afterStartIdx, final @NotNull String endSymbol) {
    int totalNumStarts = 1;
    int lookFrom = afterStartIdx;
    while (totalNumStarts > 0) {
      --totalNumStarts;
      int nextEndIdx = text.indexOf(endSymbol, lookFrom);
      if (nextEndIdx == -1) return -1;
      final int numStarts = getOccurrenceCount(text, lookFrom, nextEndIdx, shortStartSymbol);
      final int numEnds = getOccurrenceCount(text, lookFrom, nextEndIdx, shortEndSymbol);
      totalNumStarts += numStarts - numEnds;
      lookFrom = nextEndIdx + 1;
      if (totalNumStarts <= 0) return nextEndIdx;
    }
    return -1;
  }

  private static int findForLongSymbols(@NotNull final String text, int afterStartIdx,
                                                         final @NotNull String startSymbol, final @NotNull String endSymbol) {
    int totalNumStarts = 1;
    int lookFrom = afterStartIdx;
    while (totalNumStarts > 0) {
      --totalNumStarts;
      int nextEndIdx = text.indexOf(endSymbol, lookFrom);
      if (nextEndIdx == -1) return -1;
      final int numStarts = getOccurrenceCount(text, lookFrom, nextEndIdx, startSymbol);
      if (numStarts > 0) {
        totalNumStarts += numStarts;
      }
      lookFrom = nextEndIdx + endSymbol.length();
      if (totalNumStarts == 0) return nextEndIdx;
    }
    return -1;
  }

  @Contract(pure = true)
  private static int getOccurrenceCount(@NotNull String text, final int from, final int toExcluding, final char c) {
    int res = 0;
    int i = from;
    final int limit = min(text.length(), toExcluding);
    while (i < limit) {
      i = text.indexOf(c, i);
      if (i >= 0 && i < limit) {
        res++;
        i++;
      }
      else {
        break;
      }
    }
    return res;
  }

  @Contract(pure = true)
  private static int getOccurrenceCount(@NotNull String text, final int from, final int toExcluding, final String s) {
    int res = 0;
    int i = from;
    final int limit = min(text.length(), toExcluding);
    while (i < limit) {
      i = text.indexOf(s, i);
      if (i >= 0 && i < limit) {
        res++;
        i+=s.length();
      }
      else {
        break;
      }
    }
    return res;
  }
}
