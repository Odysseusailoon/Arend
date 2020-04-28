package org.arend.repl;

import org.arend.core.expr.Expression;
import org.arend.core.expr.visitor.ToAbstractVisitor;
import org.arend.error.ListErrorReporter;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.error.GeneralError;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.reference.Precedence;
import org.arend.extImpl.DefinitionRequester;
import org.arend.library.Library;
import org.arend.library.LibraryDependency;
import org.arend.library.LibraryManager;
import org.arend.library.resolver.LibraryResolver;
import org.arend.naming.reference.TCReferable;
import org.arend.naming.reference.converter.IdReferableConverter;
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor;
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor;
import org.arend.naming.scope.CachingScope;
import org.arend.naming.scope.MergeScope;
import org.arend.naming.scope.Scope;
import org.arend.naming.scope.ScopeFactory;
import org.arend.prelude.PreludeLibrary;
import org.arend.prelude.PreludeResourceLibrary;
import org.arend.repl.action.*;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.FileGroup;
import org.arend.term.prettyprint.PrettyPrintVisitor;
import org.arend.typechecking.LibraryArendExtensionProvider;
import org.arend.typechecking.TypecheckerState;
import org.arend.typechecking.instance.provider.InstanceProviderSet;
import org.arend.typechecking.order.PartialComparator;
import org.arend.typechecking.order.listener.TypecheckingOrderingListener;
import org.arend.typechecking.provider.ConcreteProvider;
import org.arend.typechecking.result.TypecheckingResult;
import org.arend.typechecking.visitor.CheckTypeVisitor;
import org.arend.typechecking.visitor.SyntacticDesugarVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public abstract class ReplState implements ReplApi {
  protected final PrettyPrinterConfig myPpConfig = PrettyPrinterConfig.DEFAULT;
  private final List<Scope> myMergedScopes = new ArrayList<>();
  private final List<ReplAction> myActions = new ArrayList<>();
  private final MergeScope myScope = new MergeScope(myMergedScopes);
  protected final @NotNull ListErrorReporter myErrorReporter;
  protected final @NotNull TypecheckerState myTypecheckerState;
  protected final @NotNull ReplLibrary myReplLibrary;
  protected final @NotNull LibraryManager myLibraryManager;
  protected final @NotNull ConcreteProvider myConcreteProvider;
  protected final @NotNull TypecheckingOrderingListener myTypechecking;

  private final @NotNull PrintStream myStdout;
  private final @NotNull PrintStream myStderr;

  public ReplState(@NotNull ListErrorReporter listErrorReporter,
                   @NotNull LibraryResolver libraryResolver,
                   @NotNull ConcreteProvider concreteProvider,
                   @NotNull PartialComparator<TCReferable> comparator,
                   @NotNull PrintStream stdout,
                   @NotNull PrintStream stderr,
                   @NotNull ReplLibrary replLibrary,
                   @NotNull TypecheckerState typecheckerState) {
    myErrorReporter = listErrorReporter;
    myConcreteProvider = concreteProvider;
    myTypecheckerState = typecheckerState;
    myStdout = stdout;
    myStderr = stderr;
    myReplLibrary = replLibrary;
    var instanceProviders = new InstanceProviderSet();
    myLibraryManager = new LibraryManager(libraryResolver, instanceProviders, this.myErrorReporter, this.myErrorReporter, DefinitionRequester.INSTANCE);
    myTypechecking = new TypecheckingOrderingListener(instanceProviders, myTypecheckerState, myConcreteProvider, IdReferableConverter.INSTANCE, this.myErrorReporter, comparator, new LibraryArendExtensionProvider(myLibraryManager));
  }

  public void loadPreludeLibrary() {
    var preludeLibrary = new PreludeResourceLibrary(myTypecheckerState);
    if (!myLibraryManager.loadLibrary(preludeLibrary, myTypechecking)) {
      myStderr.println("[FATAL] Failed to load Prelude");
    }
    myReplLibrary.addDependency(new LibraryDependency(preludeLibrary.getName()));
    myMergedScopes.add(PreludeLibrary.getPreludeScope());
  }

  private void loadReplLibrary() {
    if (!myLibraryManager.loadLibrary(myReplLibrary, myTypechecking))
      myStderr.println("[FATAL] Failed to load the REPL virtual library");
  }

  private boolean loadLibrary(Library library) {
    if (!myLibraryManager.loadLibrary(library, myTypechecking)) return false;
    myLibraryManager.registerDependency(myReplLibrary, library);
    return true;
  }

  public void runRepl(@NotNull InputStream inputStream) {
    loadPreludeLibrary();
    loadReplLibrary();
    initialize();

    var scanner = new Scanner(inputStream);
    while (scanner.hasNext()) {
      myStdout.print("\u03bb ");
      myStdout.flush();
      String line = scanner.nextLine();
      if (line.startsWith(":quit") || line.equals(":q")) break;
      boolean actionExecuted = false;
      for (ReplAction action : myActions)
        if (action.isApplicable(line)) {
          action.invoke(line, this, scanner);
          actionExecuted = true;
        }
      if (!actionExecuted && line.startsWith(":")) {
        eprintln("[ERROR] Unrecognized command: " + line.substring(1) + ".");
      }
    }
  }

  protected abstract @Nullable FileGroup parseStatements(String line);

  protected abstract @Nullable Concrete.Expression parseExpr(@NotNull String text);

  @Override
  public void checkStatements(@NotNull String line) {
    var group = parseStatements(line);
    if (group == null) return;
    var moduleScopeProvider = myReplLibrary.getModuleScopeProvider();
    Scope scope = CachingScope.make(ScopeFactory.forGroup(group, moduleScopeProvider));
    myMergedScopes.add(scope);
    new DefinitionResolveNameVisitor(myConcreteProvider, myErrorReporter)
        .resolveGroupWithTypes(group, null, myScope);
    if (checkErrors()) {
      myMergedScopes.remove(scope);
      return;
    }
    myLibraryManager.getInstanceProviderSet().collectInstances(group,
        CachingScope.make(ScopeFactory.parentScopeForGroup(group, moduleScopeProvider, true)),
        myConcreteProvider, null);
    if (checkErrors()) {
      myMergedScopes.remove(scope);
      return;
    }
    if (!myTypechecking.typecheckModules(Collections.singletonList(group), null)) {
      checkErrors();
      myMergedScopes.remove(scope);
    }
  }

  protected void initialize() {
    registerAction(DefaultAction.INSTANCE);
    registerAction(new ShowTypeCommand("type"));
    registerAction(new ShowTypeCommand("t"));
    registerAction(new NormalizeCommand("whnf", NormalizationMode.WHNF));
    registerAction(new NormalizeCommand("nf", NormalizationMode.NF));
    registerAction(new NormalizeCommand("rnf", NormalizationMode.RNF));
  }

  @Override
  public final void registerAction(@NotNull ReplCommand action) {
    registerAction((ReplAction) action);
  }

  protected final void registerAction(@NotNull ReplAction action) {
    myActions.add(action);
  }

  @Override
  public final boolean unregisterAction(@NotNull ReplAction action) {
    return myActions.remove(action);
  }

  @Override
  public final void clearActions() {
    myActions.clear();
  }

  private void actionLoad(String text) {
/* TODO
    var libPath = Paths.get(text);
    if (!loadLibrary(myLibraryResolver.registerLibrary(libPath)))
      myStderr.println("[ERROR] Failed to load the library specified.");
*/
  }

  @Override
  public void println(Object anything) {
    myStdout.println(anything);
  }

  @Override
  public void eprintln(Object anything) {
    myStderr.println(anything);
    myStderr.flush();
  }

  @Override
  public @NotNull StringBuilder prettyExpr(@NotNull StringBuilder builder, @NotNull Expression expression) {
    var abs = ToAbstractVisitor.convert(expression, myPpConfig);
    abs.accept(new PrettyPrintVisitor(builder, 0), new Precedence(Concrete.Expression.PREC));
    return builder;
  }

  @Override
  public @Nullable TypecheckingResult checkExpr(@NotNull Concrete.Expression expr, @Nullable Expression expectedType) {
    var result = new CheckTypeVisitor(myTypecheckerState, myErrorReporter, null, null)
        .checkExpr(expr, expectedType);
    return checkErrors() ? null : result;
  }

  @Override
  public @Nullable Concrete.Expression preprocessExpr(@NotNull String text) {
    var expr = parseExpr(text);
    if (expr == null || checkErrors()) return null;
    expr = expr
        .accept(new ExpressionResolveNameVisitor(myConcreteProvider,
            myScope, Collections.emptyList(), myErrorReporter, null), null)
        .accept(new SyntacticDesugarVisitor(myErrorReporter), null);
    if (checkErrors()) return null;
    return expr;
  }

  @Override
  public final boolean checkErrors() {
    var errorList = myErrorReporter.getErrorList();
    for (GeneralError error : errorList)
      (error.isSevere() ? myStderr : myStdout).println(error.getDoc(myPpConfig));
    boolean hasErrors = !errorList.isEmpty();
    errorList.clear();
    return hasErrors;
  }
}
