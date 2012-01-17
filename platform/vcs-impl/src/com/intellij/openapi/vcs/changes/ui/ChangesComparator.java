/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;

import java.util.Comparator;

public class ChangesComparator implements Comparator<Change> {
  private static final ChangesComparator ourFlattenedInstance = new ChangesComparator(false);
  private static final ChangesComparator ourTreeInstance = new ChangesComparator(true);
  private final boolean myTreeCompare;

  public static ChangesComparator getInstance(boolean flattened) {
    if (flattened) {
      return ourFlattenedInstance;
    } else {
      return ourTreeInstance;
    }
  }

  private ChangesComparator(boolean treeCompare) {
    myTreeCompare = treeCompare;
  }

  public int compare(final Change o1, final Change o2) {
    final FilePath filePath1 = ChangesUtil.getFilePath(o1);
    final FilePath filePath2 = ChangesUtil.getFilePath(o2);
    if (myTreeCompare) {
      final FilePath parentPath1 = filePath1.getParentPath();
      final FilePath parentPath2 = filePath2.getParentPath();
      if (parentPath1 != null && parentPath1.isUnder(parentPath2, true)) {
        return -1;
      }
      if (parentPath2 != null && parentPath2.isUnder(parentPath1, true)) {
        return 1;
      }
      return filePath1.getPath().compareToIgnoreCase(filePath2.getPath());
    }
    return filePath1.getName().compareToIgnoreCase(filePath2.getName());
  }
}
