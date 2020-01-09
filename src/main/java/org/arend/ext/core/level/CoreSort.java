package org.arend.ext.core.level;

import javax.annotation.Nullable;

public interface CoreSort {
  @Nullable CoreLevel getPLevel();
  @Nullable CoreLevel getHLevel();
  boolean isProp();
  boolean isSet();
}
