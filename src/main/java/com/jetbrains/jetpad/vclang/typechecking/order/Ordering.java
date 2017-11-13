package com.jetbrains.jetpad.vclang.typechecking.order;

import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable;
import com.jetbrains.jetpad.vclang.term.concrete.Concrete;
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.Typecheckable;
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.TypecheckingUnit;
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.ConcreteProvider;
import com.jetbrains.jetpad.vclang.typechecking.typeclass.provider.InstanceProvider;
import com.jetbrains.jetpad.vclang.typechecking.typeclass.provider.InstanceProviderSet;

import java.util.*;

public class Ordering {
  private static class DefState {
    int index, lowLink;
    boolean onStack;

    public DefState(int currentIndex) {
      index = currentIndex;
      lowLink = currentIndex;
      onStack = true;
    }
  }

  private int myIndex = 0;
  private final Stack<TypecheckingUnit> myStack = new Stack<>();
  private final Map<Typecheckable, DefState> myVertices = new HashMap<>();
  private final InstanceProviderSet myInstanceProviderSet;
  private final ConcreteProvider myConcreteProvider;
  private final DependencyListener myListener;
  private final boolean myRefToHeaders;

  public Ordering(InstanceProviderSet instanceProviderSet, ConcreteProvider concreteProvider, DependencyListener listener, boolean refToHeaders) {
    myInstanceProviderSet = instanceProviderSet;
    myConcreteProvider = concreteProvider;
    myListener = listener;
    myRefToHeaders = refToHeaders;
  }

  /* TODO[classes]
  private Concrete.ClassDefinition getEnclosingClass(Concrete.Definition definition) {
    Concrete.Definition parent = definition.getParentDefinition();
    if (parent == null) {
      return null;
    }
    if (parent instanceof Concrete.ClassDefinition && !definition.isStatic()) {
      return (Concrete.ClassDefinition) parent;
    }
    return getEnclosingClass(parent);
  }
  */

  public void doOrder(Concrete.Definition definition) {
    if (definition instanceof Concrete.ClassView) { // TODO[classes]: Typecheck class views
      return;
    }

    Typecheckable typecheckable = new Typecheckable(definition, myRefToHeaders);
    if (!myVertices.containsKey(typecheckable)) {
      doOrderRecursively(typecheckable);
    }
  }

  private enum OrderResult { REPORTED, NOT_REPORTED, RECURSION_ERROR }

  private OrderResult updateState(DefState currentState, Typecheckable dependency) {
    OrderResult ok = OrderResult.REPORTED;
    DefState state = myVertices.get(dependency);
    if (state == null) {
      ok = doOrderRecursively(dependency);
      currentState.lowLink = Math.min(currentState.lowLink, myVertices.get(dependency).lowLink);
    } else if (state.onStack) {
      currentState.lowLink = Math.min(currentState.lowLink, state.index);
    }
    return ok;
  }

  private void collectInstances(InstanceProvider instanceProvider, Stack<GlobalReferable> referables, Set<GlobalReferable> result) {
    while (!referables.isEmpty()) {
      GlobalReferable referable = referables.pop();
      if (result.contains(referable)) {
        continue;
      }
      result.add(referable);

      Concrete.ReferableDefinition definition = myConcreteProvider.getConcrete(referable);
      if (definition instanceof Concrete.ClassViewField) {
        for (Concrete.Instance instance : instanceProvider.getInstances(((Concrete.ClassViewField) definition).getOwnView())) {
          referables.push(instance.getData());
        }
      } else if (definition != null) {
        Collection<? extends Concrete.Parameter> parameters = Concrete.getParameters(definition);
        if (parameters != null) {
          for (Concrete.Parameter parameter : parameters) {
            Concrete.ClassView classView = Concrete.getUnderlyingClassView(((Concrete.TypeParameter) parameter).getType());
            if (classView != null) {
              for (Concrete.Instance instance : instanceProvider.getInstances(classView)) {
                referables.push(instance.getData());
              }
            }
          }
        }
      }
    }
  }

  private OrderResult doOrderRecursively(Typecheckable typecheckable) {
    Concrete.Definition definition = typecheckable.getDefinition();
    Concrete.ClassDefinition enclosingClass = null; // getEnclosingClass(definition); // TODO[classes]
    TypecheckingUnit unit = new TypecheckingUnit(typecheckable, enclosingClass);
    DefState currentState = new DefState(myIndex);
    myVertices.put(typecheckable, currentState);
    myIndex++;
    myStack.push(unit);

    Typecheckable header = null;
    if (!typecheckable.isHeader() && Typecheckable.hasHeader(definition)) {
      header = new Typecheckable(definition, true);
      OrderResult result = updateState(currentState, header);

      if (result == OrderResult.RECURSION_ERROR) {
        myStack.pop();
        currentState.onStack = false;
        myListener.unitFound(unit, DependencyListener.Recursion.IN_HEADER);
        return OrderResult.REPORTED;
      }

      if (result == OrderResult.REPORTED) {
        header = null;
      }
    }

    Stack<GlobalReferable> dependenciesWithoutInstances = new Stack<>(); // TODO[classes]: Replace stack with a set
    if (enclosingClass != null) {
      dependenciesWithoutInstances.add(enclosingClass.getData());
    }

    DependencyListener.Recursion recursion = DependencyListener.Recursion.NO;
    definition.accept(new DefinitionGetDependenciesVisitor(dependenciesWithoutInstances), typecheckable.isHeader());
    Collection<GlobalReferable> dependencies;
    InstanceProvider instanceProvider = myInstanceProviderSet.getInstanceProvider(definition.getData());
    if (instanceProvider == null) {
      dependencies = dependenciesWithoutInstances;
    } else {
      dependencies = new LinkedHashSet<>();
      collectInstances(instanceProvider, dependenciesWithoutInstances, (Set<GlobalReferable>) dependencies);
    }
    if (typecheckable.isHeader() && dependencies.contains(definition.getData())) {
      myStack.pop();
      currentState.onStack = false;
      return OrderResult.RECURSION_ERROR;
    }

    for (GlobalReferable referable : dependencies) {
      GlobalReferable tcReferable = referable.getTypecheckable();
      if (tcReferable.equals(definition.getData())) {
        if (referable.equals(tcReferable)) {
          recursion = DependencyListener.Recursion.IN_BODY;
        }
      } else {
        myListener.dependsOn(typecheckable, tcReferable);
        if (myListener.needsOrdering(tcReferable)) {
          Concrete.ReferableDefinition dependency = myConcreteProvider.getConcrete(tcReferable);
          if (dependency instanceof Concrete.Definition) {
            updateState(currentState, new Typecheckable((Concrete.Definition) dependency, myRefToHeaders));
          }
        }
      }
    }

    SCC scc = null;
    if (currentState.lowLink == currentState.index) {
      List<TypecheckingUnit> units = new ArrayList<>();
      do {
        unit = myStack.pop();
        myVertices.get(unit.getTypecheckable()).onStack = false;
        units.add(unit);
      } while (!unit.getTypecheckable().equals(typecheckable));
      Collections.reverse(units);
      scc = new SCC(units);

      if (myRefToHeaders) {
        myListener.sccFound(scc);
        return OrderResult.REPORTED;
      }

      if (typecheckable.isHeader() && units.size() == 1) {
        return OrderResult.NOT_REPORTED;
      }

      if (units.size() == 1) {
        myListener.unitFound(unit, recursion);
        return OrderResult.REPORTED;
      }
    }

    if (header != null) {
      myListener.sccFound(new SCC(Collections.singletonList(new TypecheckingUnit(header, enclosingClass))));
    }
    if (scc != null) {
      myListener.sccFound(scc);
    }

    return OrderResult.REPORTED;
  }
}
