package com.jetbrains.jetpad.vclang.naming.scope;

import com.jetbrains.jetpad.vclang.naming.reference.ErrorReference;
import com.jetbrains.jetpad.vclang.naming.reference.RedirectingReferableImpl;
import com.jetbrains.jetpad.vclang.naming.reference.Referable;
import com.jetbrains.jetpad.vclang.naming.reference.UnresolvedReference;
import com.jetbrains.jetpad.vclang.term.NameRenaming;
import com.jetbrains.jetpad.vclang.term.NamespaceCommand;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class NamespaceCommandNamespace implements Scope {
  private final Scope myModuleNamespace;
  private final NamespaceCommand myNamespaceCommand;

  private NamespaceCommandNamespace(Scope moduleNamespace, NamespaceCommand namespaceCommand) {
    myNamespaceCommand = namespaceCommand;
    myModuleNamespace = moduleNamespace;
  }

  public static @Nonnull Scope makeNamespace(Scope moduleNamespace, NamespaceCommand namespaceCommand) {
    return moduleNamespace == null || namespaceCommand.getOpenedReferences().isEmpty() && !namespaceCommand.isUsing() ? EmptyScope.INSTANCE : new NamespaceCommandNamespace(moduleNamespace, namespaceCommand);
  }

  @Nonnull
  @Override
  public Collection<? extends Referable> getElements() {
    Set<String> hidden = new HashSet<>();
    for (Referable hiddenElement : myNamespaceCommand.getHiddenReferences()) {
      hidden.add(hiddenElement.textRepresentation());
    }

    List<Referable> elements = new ArrayList<>();
    Collection<? extends NameRenaming> opened = myNamespaceCommand.getOpenedReferences();
    for (NameRenaming renaming : opened) {
      Referable oldRef = renaming.getOldReference();
      if (oldRef instanceof UnresolvedReference) {
        oldRef = ((UnresolvedReference) oldRef).resolve(myModuleNamespace);
      }
      if (!(oldRef instanceof ErrorReference)) {
        String newName = renaming.getName();
        String name = newName != null ? newName : oldRef.textRepresentation();
        if (!hidden.contains(name)) {
          elements.add(newName != null ? new RedirectingReferableImpl(oldRef, renaming.getPrecedence(), newName) : oldRef);
        }
        hidden.add(oldRef.textRepresentation());
      }
    }

    if (myNamespaceCommand.isUsing()) {
      elemLoop:
      for (Referable ref : myModuleNamespace.getElements()) {
        if (hidden.contains(ref.textRepresentation())) {
          continue;
        }

        for (NameRenaming renaming : opened) {
          if (renaming.getOldReference().textRepresentation().equals(ref.textRepresentation())) {
            continue elemLoop;
          }
        }

        elements.add(ref);
      }
    }

    return elements;
  }

  @Nullable
  @Override
  public Referable resolveName(String name) {
    for (Referable hiddenRef : myNamespaceCommand.getHiddenReferences()) {
      if (hiddenRef.textRepresentation().equals(name)) {
        return null;
      }
    }

    Collection<? extends NameRenaming> opened = myNamespaceCommand.getOpenedReferences();
    for (NameRenaming renaming : opened) {
      String newName = renaming.getName();
      Referable oldRef = renaming.getOldReference();
      if ((newName != null ? newName : oldRef.textRepresentation()).equals(name)) {
        if (oldRef instanceof UnresolvedReference) {
          oldRef = ((UnresolvedReference) oldRef).resolve(myModuleNamespace);
        }
        return newName != null ? new RedirectingReferableImpl(oldRef, renaming.getPrecedence(), newName) : oldRef;
      }
    }

    if (!myNamespaceCommand.isUsing()) {
      return null;
    }

    for (NameRenaming renaming : opened) {
      if (renaming.getOldReference().textRepresentation().equals(name)) {
        return null;
      }
    }

    return myModuleNamespace.resolveName(name);
  }

  @Nullable
  @Override
  public Scope resolveNamespace(String name) {
    for (Referable hiddenRef : myNamespaceCommand.getHiddenReferences()) {
      if (hiddenRef.textRepresentation().equals(name)) {
        return null;
      }
    }

    Collection<? extends NameRenaming> opened = myNamespaceCommand.getOpenedReferences();
    for (NameRenaming renaming : opened) {
      String newName = renaming.getName();
      Referable oldRef = renaming.getOldReference();
      if ((newName != null ? newName : oldRef.textRepresentation()).equals(name)) {
        return myModuleNamespace.resolveNamespace(oldRef.textRepresentation());
      }
    }

    if (!myNamespaceCommand.isUsing()) {
      return null;
    }

    for (NameRenaming renaming : opened) {
      if (renaming.getOldReference().textRepresentation().equals(name)) {
        return null;
      }
    }

    return myModuleNamespace.resolveNamespace(name);
  }
}