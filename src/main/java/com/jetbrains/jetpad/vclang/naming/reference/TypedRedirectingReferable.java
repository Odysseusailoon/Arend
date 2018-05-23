package com.jetbrains.jetpad.vclang.naming.reference;

import com.jetbrains.jetpad.vclang.term.Precedence;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TypedRedirectingReferable implements RedirectingReferable {
  private final Referable myOriginalReferable;
  private final ClassReferable myTypeClassReference;

  public TypedRedirectingReferable(Referable originalReferable, ClassReferable typeClassReference) {
    myOriginalReferable = originalReferable;
    myTypeClassReference = typeClassReference;
  }

  @Nonnull
  @Override
  public Precedence getPrecedence() {
    return myOriginalReferable instanceof GlobalReferable ? ((GlobalReferable) myOriginalReferable).getPrecedence() : Precedence.DEFAULT;
  }

  @Nonnull
  @Override
  public Referable getOriginalReferable() {
    return myOriginalReferable instanceof RedirectingReferable ? ((RedirectingReferable) myOriginalReferable).getOriginalReferable() : myOriginalReferable;
  }

  @Nonnull
  @Override
  public String textRepresentation() {
    return myOriginalReferable.textRepresentation();
  }

  @Override
  public GlobalReferable getTypecheckable() {
    return myOriginalReferable instanceof GlobalReferable ? ((GlobalReferable) myOriginalReferable).getTypecheckable() : null;
  }

  @Override
  public boolean isTypecheckable() {
    return myOriginalReferable instanceof GlobalReferable && ((GlobalReferable) myOriginalReferable).isTypecheckable();
  }

  @Nullable
  @Override
  public ClassReferable getTypeClassReference() {
    return myTypeClassReference;
  }
}