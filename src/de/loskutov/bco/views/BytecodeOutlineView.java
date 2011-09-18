/*****************************************************************************************
 * Copyright (c) 2011 Andrey Loskutov. All rights reserved. This program and the
 * accompanying materials are made available under the terms of the BSD License which
 * accompanies this distribution, and is available at
 * http://www.opensource.org/licenses/bsd-license.php Contributor: Andrey Loskutov -
 * initial API and implementation
 ****************************************************************************************/
package de.loskutov.bco.views;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.StatusLineManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IFindReplaceTarget;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextListener;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.TextEvent;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.console.actions.TextViewerAction;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.ui.texteditor.FindReplaceAction;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.IUpdate;
import org.objectweb.asm.tree.ClassNode;

import de.loskutov.bco.BytecodeOutlinePlugin;
import de.loskutov.bco.asm.DecompiledClass;
import de.loskutov.bco.asm.DecompiledMethod;
import de.loskutov.bco.asm.DecompilerHelper;
import de.loskutov.bco.asm.DecompilerOptions;
import de.loskutov.bco.asm.LineRange;
import de.loskutov.bco.preferences.BCOConstants;
import de.loskutov.bco.ui.EclipseUtils;
import de.loskutov.bco.ui.JdtUtils;
import de.loskutov.bco.ui.actions.DefaultToggleAction;

/**
 * This view shows decompiled java bytecode
 * @author Andrei
 */
public class BytecodeOutlineView extends ViewPart {

    // orientations
    static final int VIEW_ORIENTATION_VERTICAL = 0;
    static final int VIEW_ORIENTATION_HORIZONTAL = 1;
    static final int VIEW_ORIENTATION_AUTOMATIC = 2;

    /**
     * The current orientation; either <code>VIEW_ORIENTATION_HORIZONTAL</code>
     * <code>VIEW_ORIENTATION_VERTICAL</code>,
     * or <code>VIEW_ORIENTATION_AUTOMATIC</code>.
     */
    int orientation = VIEW_ORIENTATION_AUTOMATIC;
    /**
     * The current orientation; either <code>VIEW_ORIENTATION_HORIZONTAL</code>
     * <code>VIEW_ORIENTATION_VERTICAL</code>.
     */
    private int currentOrientation;

    protected ToggleOrientationAction[] toggleOrientationActions;

    protected BitSet modes;

    protected boolean inputChanged;
    protected boolean bufferIsDirty;

    private boolean isEnabled;
    private boolean isActive;
    private boolean isVisible;

    protected Composite stackComposite;
    protected StyledText textControl;
    protected TextViewer textViewer;
    protected SashForm verifyControl;
    protected SashForm stackAndLvt;
    protected Table tableControl;
    protected TableViewer tableControlViewer;
    protected Table stackTable;
    protected Table lvtTable;

    protected ITextEditor javaEditor;
    private IJavaElement javaInput;
    protected IJavaElement lastChildElement;
    protected ITextSelection currentSelection;
    protected EditorListener editorListener;
    protected Action selectionChangedAction;
    protected Action refreshVarsAndStackAction;
    protected DefaultToggleAction linkWithEditorAction;
    protected DefaultToggleAction showSelectedOnlyAction;
    protected DefaultToggleAction setRawModeAction;
    protected DefaultToggleAction toggleASMifierModeAction;
    protected DefaultToggleAction hideLineInfoAction;
    protected DefaultToggleAction hideLocalsAction;
    protected DefaultToggleAction hideStackMapAction;
    protected DefaultToggleAction showHexValuesAction;
    protected DefaultToggleAction expandStackMapAction;
    protected DefaultToggleAction toggleVerifierAction;
    protected StatusLineManager statusLineManager;
    protected BCOViewSelectionProvider viewSelectionProvider;

    protected Color errorColor;

    private DecompiledClass lastDecompiledResult;

    protected Map<String, IAction> globalActions;
    protected List<String> selectionActions;
    private MenuManager contextMenuManager;
    /** global class info, without current selection status */
    protected String currentStatusMessage;
    protected boolean hasAnalyzerError;
    /*
     * I don't know how to update the state of toolbar and menu managers because it seems
     * that if we toggle the action state internally (not by user click) then either the
     * managers or contribution items or whatever holds the old state of checked action.
     * This flag is a workaround and allows us restore the state after internal toggling.
     */
    private boolean restoreVerify;
    private static final String NLS_PREFIX = "BytecodeOutlineView.";

    // updates the find replace action if the document length is > 0
    private ITextListener textListener;

    // see org.eclipse.ui.console.TextConsolePage for the reason to do this ;)
    private ISelectionChangedListener textSelectionListener;
    private Control statusControl;

    // ------------------------------------------------------------------------

    protected void setJavaInput(IJavaElement javaInput) {
        this.javaInput = javaInput;
        inputChanged = true;
    }

    /**
     * The constructor.
     */
    public BytecodeOutlineView() {
        super();
        modes = new BitSet();
        globalActions = new HashMap<String, IAction>();
        selectionActions = new ArrayList<String>();
    }

    // ------------------------------------------------------------------------

    /**
     * Is this view state changes depending on editor changes?
     * @return true if linked with editor
     */
    protected boolean isLinkedWithEditor() {
        return modes.get(BCOConstants.F_LINK_VIEW_TO_EDITOR);
    }

    /**
     * Are actions on toolbar active?
     * @return Returns the isEnabled.
     */
    private boolean isEnabled() {
        return isEnabled;
    }

    private void setEnabled(boolean on) {
        this.isEnabled = on;
        if (tableControl != null && !tableControl.isDisposed()) {
            tableControl.setEnabled(on);
        }
        if (stackTable != null && !stackTable.isDisposed()) {
            stackTable.setEnabled(on);
        }
        if (lvtTable != null && !lvtTable.isDisposed()) {
            lvtTable.setEnabled(on);
        }
        showSelectedOnlyAction.setEnabled(on);
        // linkWithEditorAction.setEnabled(on);
        selectionChangedAction.setEnabled(on);
        toggleVerifierAction.setEnabled(on);
        hideLocalsAction.setEnabled(on);
        hideLineInfoAction.setEnabled(on);
        hideStackMapAction.setEnabled(on);
        showHexValuesAction.setEnabled(on);
        toggleASMifierModeAction.setEnabled(on);
        expandStackMapAction.setEnabled(on);
        setRawModeAction
            .setEnabled(on && !toggleASMifierModeAction.isChecked());
        boolean showAnalyzer = on && toggleVerifierAction.isChecked();
        for (int i = 0; i < toggleOrientationActions.length; ++i) {
            toggleOrientationActions[i].setEnabled(showAnalyzer);
        }
    }

    /**
     * Is this view monitoring workspace changes?
     * @return Returns the isActive.
     */
    private boolean isActive() {
        return isActive;
    }

    /**
     * @param bufferIsDirty The bufferIsDirty to set.
     */
    private void setBufferIsDirty(boolean bufferIsDirty) {
        this.bufferIsDirty = bufferIsDirty;
    }

    private void setInput(ITextEditor editor) {
        javaEditor = null;
        setJavaInput(null);
        lastDecompiledResult = null;
        if (editor != null) {
            IJavaElement javaElem = EclipseUtils.getJavaInput(editor);
            if (javaElem == null) {
                return;
            }
            setJavaInput(javaElem);
            javaEditor = editor;

            checkVerifyMode();

            updateSelection(EclipseUtils.getSelection(javaEditor
                .getSelectionProvider()));
            setBufferIsDirty(editor.isDirty());
        }
    }

    private void checkVerifyMode() {
        boolean aoi = JdtUtils.isAbstractOrInterface(javaInput);

        if (!toggleVerifierAction.isChecked()) {
            // deactivate verify button, but only if *not* in verify mode
            toggleVerifierAction.setEnabled(!aoi);
            restoreVerify = false;
        } else {
            if (aoi) {
                // swith verify mode off, because it is not applicable to selected element
                inputChanged = true;
                toggleVerifyMode(
                    getViewSite().getActionBars().getMenuManager(), false);
                // remember last state, to match the state of the toolbars and menus
                restoreVerify = true;
            } else {
                if (restoreVerify) {
                    inputChanged = true;
                    toggleVerifierAction.setEnabled(true);
                    toggleVerifyMode(getViewSite().getActionBars()
                        .getMenuManager(), true);
                }
                restoreVerify = false;
            }
        }
    }

    private boolean updateSelection(ITextSelection sel) {
        if (sel != null
            && (sel.equals(currentSelection) || (currentSelection != null
                && sel.getStartLine() == currentSelection.getStartLine() && sel
                .getEndLine() == currentSelection.getEndLine()))) {

            /*
             * getStartLine/getEndLine is probably not sensitive enough - but in case of
             * java classes/methods which fits in one selection but not in the other, then
             * I think we can ignore them here - this is not the 99% of use cases.
             */
            return false;
        }

        currentSelection = sel;
        return true;
    }

    // ------------------------------------------------------------------------

    /**
     * @see org.eclipse.ui.IViewPart#init(org.eclipse.ui.IViewSite)
     */
    @Override
    public void init(IViewSite site) {
        super.setSite(site);
        if (editorListener == null) {
            editorListener = new EditorListener(this);
            getSite().getWorkbenchWindow().getPartService().addPartListener(
                editorListener);
        }
    }

    /**
     * This is a callback that will allow us to create the viewer and initialize it.
     * @param parent
     */
    @Override
    public void createPartControl(Composite parent) {
        errorColor = parent.getDisplay().getSystemColor(SWT.COLOR_RED);
        parent.addControlListener(new ControlListener() {

            @Override
            public void controlMoved(ControlEvent e) {
                //
            }

            @Override
            public void controlResized(ControlEvent e) {
                computeOrientation();
            }
        });

        GridLayout parentLayout = new GridLayout();
        parentLayout.numColumns = 1;
        parentLayout.marginBottom = -5;
        parentLayout.marginTop = -5;
        parentLayout.marginLeft = -5;
        parentLayout.marginRight = -5;

        parent.setLayout(parentLayout);

        stackComposite = new Composite(parent, SWT.NONE);
        stackComposite.setLayoutData(new GridData(GridData.FILL_BOTH));
        stackComposite.setLayout(new StackLayout());

        statusLineManager = new StatusLineManager();
        statusControl = statusLineManager.createControl(parent, SWT.NONE);
        statusControl.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        createTextControl();

        createTextContextMenu();

        createVerifyControl();

        initModes();

        if (modes.get(BCOConstants.F_SHOW_ANALYZER)) {
            ((StackLayout) stackComposite.getLayout()).topControl = verifyControl;
        } else {
            ((StackLayout) stackComposite.getLayout()).topControl = textControl;
        }

        createSelectionProvider();

        createToolbarActions();

        setEnabled(false);

//        activateView();
    }

    private void initModes() {
        IPreferenceStore store = BytecodeOutlinePlugin.getDefault()
            .getPreferenceStore();
        modes.set(BCOConstants.F_LINK_VIEW_TO_EDITOR, store
            .getBoolean(BCOConstants.LINK_VIEW_TO_EDITOR));
        modes.set(BCOConstants.F_SHOW_ONLY_SELECTED_ELEMENT, store
            .getBoolean(BCOConstants.SHOW_ONLY_SELECTED_ELEMENT));
        modes.set(BCOConstants.F_SHOW_RAW_BYTECODE, store
            .getBoolean(BCOConstants.SHOW_RAW_BYTECODE));
        modes.set(BCOConstants.F_SHOW_LINE_INFO, store
            .getBoolean(BCOConstants.SHOW_LINE_INFO));
        modes.set(BCOConstants.F_SHOW_VARIABLES, store
            .getBoolean(BCOConstants.SHOW_VARIABLES));
        modes.set(BCOConstants.F_SHOW_STACKMAP, store
            .getBoolean(BCOConstants.SHOW_STACKMAP));
        modes.set(BCOConstants.F_EXPAND_STACKMAP, store
            .getBoolean(BCOConstants.EXPAND_STACKMAP));
        modes.set(BCOConstants.F_SHOW_ASMIFIER_CODE, store
            .getBoolean(BCOConstants.SHOW_ASMIFIER_CODE));
        modes.set(BCOConstants.F_SHOW_ANALYZER, store
            .getBoolean(BCOConstants.SHOW_ANALYZER));
        modes.set(BCOConstants.F_SHOW_HEX_VALUES, store
            .getBoolean(BCOConstants.SHOW_HEX_VALUES));
    }

    private void createToolbarActions() {
        createTextActions();

        final IActionBars bars = getViewSite().getActionBars();
        final IToolBarManager tmanager = bars.getToolBarManager();
        final IMenuManager mmanager = bars.getMenuManager();

        selectionChangedAction = new Action() {

            @Override
            public void run() {
                Point selection = textControl.getSelection();
                setSelectionInJavaEditor(selection);
            }
        };

        refreshVarsAndStackAction = new Action() {

            @Override
            public void run() {
                int selectionIndex = tableControl.getSelectionIndex();
                TableItem[] items = tableControl.getSelection();
                if(items == null || items.length < 1){
                    return;
                }
                String line = items[0].getText(0);
                if(line == null || "".equals(line)){
                    return;
                }
                Integer valueOf = Integer.valueOf(line);
                if(valueOf != null){
                    updateVerifierControl4insn(valueOf.intValue());
                    tableControl.setSelection(selectionIndex);
                }
            }
        };

        linkWithEditorAction = new DefaultToggleAction(
            BCOConstants.LINK_VIEW_TO_EDITOR) {

            @Override
            public void run(boolean newState) {
                setMode(BCOConstants.F_LINK_VIEW_TO_EDITOR, newState);
                if (modes.get(BCOConstants.F_LINK_VIEW_TO_EDITOR)) {
                    showSelectedOnlyAction.setEnabled(true);
                    toggleVerifierAction.setEnabled(true);
                    hideLineInfoAction.setEnabled(true);
                    hideLocalsAction.setEnabled(true);
                    toggleASMifierModeAction.setEnabled(true);
                    if (!toggleASMifierModeAction.isChecked()) {
                        setRawModeAction.setEnabled(true);
                    }
                    activateView();
                    checkOpenEditors(true);
                    inputChanged = true;
                    refreshView();
                }
            }
        };

        showSelectedOnlyAction = new DefaultToggleAction(
            BCOConstants.SHOW_ONLY_SELECTED_ELEMENT) {

            @Override
            public void run(boolean newState) {
                setMode(BCOConstants.F_SHOW_ONLY_SELECTED_ELEMENT, newState);
                inputChanged = true;
                refreshView();
            }
        };

        setRawModeAction = new DefaultToggleAction(
            BCOConstants.SHOW_RAW_BYTECODE) {

            @Override
            public void run(boolean newState) {
                setMode(BCOConstants.F_SHOW_RAW_BYTECODE, newState);
                inputChanged = true;
                refreshView();
            }
        };

        hideLineInfoAction = new DefaultToggleAction(
            BCOConstants.SHOW_LINE_INFO) {

            @Override
            public void run(boolean newState) {
                setMode(BCOConstants.F_SHOW_LINE_INFO, newState);
                inputChanged = true;
                refreshView();
            }
        };

        hideLocalsAction = new DefaultToggleAction(BCOConstants.SHOW_VARIABLES) {

            @Override
            public void run(boolean newState) {
                setMode(BCOConstants.F_SHOW_VARIABLES, newState);
                inputChanged = true;
                refreshView();
            }
        };

        hideStackMapAction = new DefaultToggleAction(BCOConstants.SHOW_STACKMAP) {

            @Override
            public void run(boolean newState) {
                setMode(BCOConstants.F_SHOW_STACKMAP, newState);
                inputChanged = true;
                refreshView();
            }
        };

        expandStackMapAction = new DefaultToggleAction(
            BCOConstants.EXPAND_STACKMAP) {

            @Override
            public void run(boolean newState) {
                setMode(BCOConstants.F_EXPAND_STACKMAP, newState);
                inputChanged = true;
                refreshView();
            }
        };

        showHexValuesAction = new DefaultToggleAction(
            BCOConstants.SHOW_HEX_VALUES) {

            @Override
            public void run(boolean newState) {
                setMode(BCOConstants.F_SHOW_HEX_VALUES, newState);
                inputChanged = true;
                refreshView();
            }
        };

        toggleASMifierModeAction = new DefaultToggleAction(
            BCOConstants.SHOW_ASMIFIER_CODE) {

            @Override
            public void run(boolean newState) {
                setMode(BCOConstants.F_SHOW_ASMIFIER_CODE, newState);
                if (newState) {
                    setMode(BCOConstants.F_SHOW_RAW_BYTECODE, true);
                    setRawModeAction.setEnabled(false);
                } else {
                    setRawModeAction.setEnabled(true);
                }
                inputChanged = true;
                refreshView();
            }
        };

        toggleVerifierAction = new DefaultToggleAction(
            BCOConstants.SHOW_ANALYZER) {

            @Override
            public void run(boolean newState) {
                toggleVerifyMode(mmanager, newState);
                inputChanged = true;
                refreshView();
            }
        };

        mmanager.add(linkWithEditorAction);
        mmanager.add(showSelectedOnlyAction);
        mmanager.add(setRawModeAction);
        mmanager.add(hideLineInfoAction);
        mmanager.add(hideLocalsAction);
        mmanager.add(showHexValuesAction);
        mmanager.add(hideStackMapAction);
        mmanager.add(expandStackMapAction);
        mmanager.add(toggleASMifierModeAction);
        mmanager.add(toggleVerifierAction);

        mmanager.add(new Separator());

        toggleOrientationActions = new ToggleOrientationAction[]{
            new ToggleOrientationAction(this, VIEW_ORIENTATION_VERTICAL),
            new ToggleOrientationAction(this, VIEW_ORIENTATION_HORIZONTAL),
            new ToggleOrientationAction(this, VIEW_ORIENTATION_AUTOMATIC)};
        for (int i = 0; i < toggleOrientationActions.length; ++i) {
            mmanager.add(toggleOrientationActions[i]);
        }

        tmanager.add(linkWithEditorAction);
        tmanager.add(showSelectedOnlyAction);
        tmanager.add(setRawModeAction);
        // tmanager.add(hideLineInfoAction);
        // tmanager.add(hideLocalsAction);
        tmanager.add(toggleASMifierModeAction);
        tmanager.add(toggleVerifierAction);
    }

    @SuppressWarnings("unused")
    private void createVerifyControl() {
        verifyControl = new SashForm(stackComposite, SWT.VERTICAL);

        tableControl = new Table(verifyControl, SWT.SINGLE | SWT.FULL_SELECTION);
        tableControlViewer = new TableViewer(tableControl);

        TableColumn tc = new TableColumn(tableControl, SWT.LEFT);
        tc.setText("#");
        tc.setToolTipText("ASM instruction offset");

        tc = new TableColumn(tableControl, SWT.LEFT);
        tc.setText(BytecodeOutlinePlugin.getResourceString(NLS_PREFIX + "lvt.header"));
        tc.setToolTipText("Local variables");

        tc = new TableColumn(tableControl, SWT.LEFT);
        tc.setText(BytecodeOutlinePlugin.getResourceString(NLS_PREFIX + "stack.header"));
        tc.setToolTipText("Stack content *before* current instruction is executed");
        new TableColumn(tableControl, SWT.LEFT);
        new TableColumn(tableControl, SWT.LEFT);
        tableControl.setLinesVisible(false);
        tableControl.setHeaderVisible(true);

        stackAndLvt = new SashForm(verifyControl, SWT.HORIZONTAL);

        lvtTable = new Table(stackAndLvt, SWT.SINGLE | SWT.FULL_SELECTION);
        lvtTable.setLinesVisible(false);
        lvtTable.setHeaderVisible(true);

        new TableColumn(lvtTable, SWT.LEFT).setText("#");
        new TableColumn(lvtTable, SWT.LEFT).setText("Var Type");
        new TableColumn(lvtTable, SWT.LEFT).setText("Name");

        stackTable = new Table(stackAndLvt, SWT.SINGLE | SWT.FULL_SELECTION);
        stackTable.setLinesVisible(false);
        stackTable.setHeaderVisible(true);
        new TableColumn(stackTable, SWT.LEFT).setText("#");
        new TableColumn(stackTable, SWT.LEFT).setText("Stack Type");

        stackAndLvt.setWeights(new int[]{50, 50});

        verifyControl.setWeights(new int[]{75, 25});

        tableControl.addSelectionListener(new SelectionAdapter() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                if (modes.get(BCOConstants.F_LINK_VIEW_TO_EDITOR)) {
                    selectionChangedAction.run();
                }
                refreshVarsAndStackAction.run();
            }
        });

    }

    private void createSelectionProvider() {
        viewSelectionProvider = new BCOViewSelectionProvider();
        viewSelectionProvider.registerSelectionProvider(textViewer);
        viewSelectionProvider.registerSelectionProvider(tableControlViewer);

        if (modes.get(BCOConstants.F_SHOW_ANALYZER)) {
            viewSelectionProvider.setCurrentSelectionProvider(tableControlViewer);
        } else {
            viewSelectionProvider.setCurrentSelectionProvider(textViewer);
        }
        getSite().setSelectionProvider(viewSelectionProvider);
    }

    /**
     * create/register context menu on text control
     */
    private void createTextContextMenu() {
        String id = "de.loskutov.bco.views.BytecodeOutlineView#ContextMenu"; //$NON-NLS-1$
        contextMenuManager = new MenuManager("#ContextMenu", id); //$NON-NLS-1$
        contextMenuManager.setRemoveAllWhenShown(true);
        contextMenuManager.addMenuListener(new IMenuListener() {

            @Override
            public void menuAboutToShow(IMenuManager m) {
                contextMenuAboutToShow(m);
            }
        });
        Menu menu = contextMenuManager.createContextMenu(textControl);
        textControl.setMenu(menu);

        getSite().registerContextMenu(id, contextMenuManager, textViewer);
    }

    private void createTextControl() {
        textViewer = new TextViewer(stackComposite, SWT.H_SCROLL | SWT.V_SCROLL);
        textViewer.setEditable(false);
        // textViewer.setHoverControlCreator(null)

        textControl = textViewer.getTextWidget();
        IDocument document = new Document("");
        textViewer.setDocument(document);

        textSelectionListener = new ISelectionChangedListener() {

            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                // Updates selection dependent actions like find/copy.
                Iterator<String> iterator = selectionActions.iterator();
                while (iterator.hasNext()) {
                    updateAction(iterator.next());
                }
            }
        };

        textListener = new ITextListener() {

            @Override
            public void textChanged(TextEvent event) {
                IUpdate findReplace = (IUpdate) globalActions
                    .get(ActionFactory.FIND.getId());
                if (findReplace != null) {
                    findReplace.update();
                }
            }
        };

        textViewer.getSelectionProvider().addSelectionChangedListener(
            textSelectionListener);
        textViewer.addTextListener(textListener);

        textControl.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseDown(MouseEvent e) {
                if (modes.get(BCOConstants.F_LINK_VIEW_TO_EDITOR)) {
                    selectionChangedAction.run();
                }
            }
            @Override
            public void mouseUp(MouseEvent e) {
                if (modes.get(BCOConstants.F_LINK_VIEW_TO_EDITOR)) {
                    selectionChangedAction.run();
                }
            }
        });

        textControl.addKeyListener(new KeyListener() {

            @Override
            public void keyPressed(KeyEvent e) {
                // ignored
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (modes.get(BCOConstants.F_LINK_VIEW_TO_EDITOR)) {
                    selectionChangedAction.run();
                }
            }
        });
    }

    /**
     * @see org.eclipse.ui.IWorkbenchPart#dispose()
     */
    @Override
    public void dispose() {
        deActivateView();
        if (editorListener != null) {
            getSite().getWorkbenchWindow().getPartService().removePartListener(
                editorListener);
            editorListener.dispose();
            editorListener = null;
        }

        if (contextMenuManager != null) {
            contextMenuManager.dispose();
        }

        selectionActions.clear();
        globalActions.clear();

        textViewer.getSelectionProvider().removeSelectionChangedListener(
            textSelectionListener);
        textViewer.removeTextListener(textListener);
        textViewer = null;
        viewSelectionProvider = null;

        if (textControl != null) {
            textControl.dispose();
            textControl = null;
        }
        if (verifyControl != null) {
            verifyControl.dispose();
            verifyControl = null;
            tableControl = null;
            stackTable = null;
            lvtTable = null;
            tableControlViewer = null;
        }
        currentSelection = null;
        javaEditor = null;
        setJavaInput(null);
        lastChildElement = null;
        lastDecompiledResult = null;

        linkWithEditorAction.dispose();
        showSelectedOnlyAction.dispose();
        setRawModeAction.dispose();
        toggleASMifierModeAction.dispose();
        hideLineInfoAction.dispose();
        hideLocalsAction.dispose();
        hideStackMapAction.dispose();
        showHexValuesAction.dispose();
        expandStackMapAction.dispose();
        toggleVerifierAction.dispose();

        linkWithEditorAction = null;
        selectionChangedAction = null;
        refreshVarsAndStackAction = null;
        showSelectedOnlyAction = null;
        setRawModeAction = null;
        toggleASMifierModeAction = null;
        hideLineInfoAction = null;
        hideLocalsAction = null;
        hideStackMapAction = null;
        showHexValuesAction = null;
        expandStackMapAction = null;
        toggleVerifierAction = null;
        super.dispose();
    }

    /**
     * Fill the context menu
     * @param menuManager menu
     */
    protected void contextMenuAboutToShow(IMenuManager menuManager) {
        IDocument doc = textViewer.getDocument();
        if (doc == null) {
            return;
        }

        menuManager
            .add(globalActions.get(ActionFactory.COPY.getId()));
        menuManager.add(globalActions.get(ActionFactory.SELECT_ALL
            .getId()));

        menuManager.add(new Separator("FIND")); //$NON-NLS-1$
        menuManager
            .add(globalActions.get(ActionFactory.FIND.getId()));

        menuManager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
    }

    // -----------------------------------------------------------------------

    /**
     * Passing the focus request to the viewer's control.
     */
    @Override
    public void setFocus() {
        if (!modes.get(BCOConstants.F_SHOW_ANALYZER)) {
            if (textViewer != null) {
                textViewer.getTextWidget().setFocus();
            }
        } else {
            if (tableControl != null) {
                tableControl.setFocus();
            }
        }
    }

    protected void handleBufferIsDirty(boolean isDirty) {
        if (!modes.get(BCOConstants.F_LINK_VIEW_TO_EDITOR) || !isActive()) {
            return;
        }
        if (isDirty) {
            setBufferIsDirty(isDirty);
        } else {
            if (!bufferIsDirty) {
                // second time calling with same argument -
                // cause new bytecode should be written now
                inputChanged = true;
                refreshView();
            } else {
                // first time - set the flag only - cause
                // bytecode is not yet written
                setBufferIsDirty(false);
            }
        }
    }

    protected void handlePartHidden(IWorkbenchPart part) {
        if (!modes.get(BCOConstants.F_LINK_VIEW_TO_EDITOR)) {
            return;
        }
        if (this == part) {
            isVisible = false;
            deActivateView();
        } else if (isActive() && (part instanceof IEditorPart)) {
            // check if at least one editor is open
            checkOpenEditors(false);
        }
    }

    protected void handlePartVisible(IWorkbenchPart part) {
        if (!modes.get(BCOConstants.F_LINK_VIEW_TO_EDITOR)) {
            if(this == part){
                isVisible = true;
            }
            return;
        }
        if (this == part) {
            if (isVisible) {
                return;
            }
            isVisible = true;
            // check if java editor is already open
            IEditorPart activeEditor = EclipseUtils.getActiveEditor();
            if (!(activeEditor instanceof ITextEditor)) {
                // start monitoring again, even if current editor is not
                // supported - but we at front now
                activateView();
                return;
            }
            part = activeEditor;
            // continue with setting input
        }
        if (isVisible && part instanceof ITextEditor) {
            if (isActive() && part == javaEditor) {
                return;
            }
            activateView();
            setEnabled(true);
            setInput((ITextEditor) part);
            refreshView();
        } else if (part instanceof IEditorPart) {
            if (isActive()) {
                deActivateView();
            }
        }
    }

    protected void handleSelectionChanged(IWorkbenchPart part,
        ISelection selection) {
        if (!modes.get(BCOConstants.F_LINK_VIEW_TO_EDITOR) || !isActive()
            || !isVisible || !(part instanceof IEditorPart)) {
            return;
        }
        if (!(part instanceof ITextEditor)) {
            deActivateView();
            return;
        }
        if (!isEnabled()) {
            setEnabled(true);
        }
        if (part != javaEditor) {
            setInput((ITextEditor) part);
        } else {
            if (!updateSelection((ITextSelection) selection)) {
                return;
            }
        }
        refreshView();
    }

    /**
     * Does nothing if view is already active
     */
    private void activateView() {
        if (isActive()) {
            return;
        }
        isActive = true;
        getSite().getWorkbenchWindow().getSelectionService()
            .addPostSelectionListener(editorListener);
        FileBuffers.getTextFileBufferManager().addFileBufferListener(
            editorListener);
    }

    /**
     * Does nothing if view is already deactivated
     */
    private void deActivateView() {
        if (!isActive()) {
            return;
        }
        setEnabled(false);
        if (editorListener != null) {
            ISelectionService service = getSite().getWorkbenchWindow()
                .getSelectionService();
            if (service != null) {
                service.removePostSelectionListener(editorListener);
            }
            FileBuffers.getTextFileBufferManager().removeFileBufferListener(
                editorListener);

        }
        if (textViewer != null && textViewer.getTextWidget() != null
            && !textViewer.getTextWidget().isDisposed()) {
            IDocument document = new Document("");
            textViewer.setDocument(document);
        }
        if (tableControl != null && !tableControl.isDisposed()) {
            setVerifyTableItems(null);
        }
        /*
         * if(stackControl != null && !stackControl.isDisposed()){
         * stackControl.setText(""); } if(lvtControl != null && !lvtControl.isDisposed()){
         * lvtControl.setText(""); }
         */
        if (stackTable != null && !stackTable.isDisposed()) {
            stackTable.removeAll();
        }
        if (lvtTable != null && !lvtTable.isDisposed()) {
            lvtTable.removeAll();
        }
        if (statusControl != null && !statusControl.isDisposed()) {
            updateStatus(null, -1, -1);
        }
        currentSelection = null;
        lastDecompiledResult = null;
        javaEditor = null;
        setJavaInput(null);
        lastChildElement = null;
        setBufferIsDirty(false);
        isActive = false;
    }

    protected void refreshView() {
        if (!isActive()) {
            return;
        }

        IJavaElement childEl = getCurrentJavaElement();
        if (childEl == null && javaInput == null) {
            setInput(javaEditor);
            childEl = javaInput;
        }

        // after getCurrentJavaElement() call it is possible that java type is disappear
        // because corresponding type is not more exist in model
        if (javaInput == null) {
            deActivateView();
            return;
        }

        boolean clearOutput = false;

        if (inputChanged || isSelectedElementChanged(childEl)) {

            DecompiledClass result = decompileBytecode(childEl);
            if (result == null) {
                clearOutput = true;
            } else {
                if (modes.get(BCOConstants.F_SHOW_ANALYZER)
                    && !result.isAbstractOrInterface()) {
                    refreshVerifyView(result);
                } else {
                    toggleVerifierAction.setEnabled(!result
                        .isAbstractOrInterface());
                    refreshTextView(result);
                }
            }
            lastDecompiledResult = result;
        } else if (childEl == null
            && modes.get(BCOConstants.F_SHOW_ONLY_SELECTED_ELEMENT)) {
            clearOutput = true;
        }

        lastChildElement = childEl;
        if (clearOutput) {
            if (!modes.get(BCOConstants.F_SHOW_ANALYZER)) {
                IDocument document = new Document("");
                textViewer.setDocument(document);
            } else {
                setVerifyTableItems(null);
            }
        }
        setSelectionInBytecodeView();
        inputChanged = false;
    }

    private void refreshTextView(DecompiledClass result) {
        IDocument document = new Document(result.getText());
        textViewer.setDocument(document);
        // we are in verify mode but we can't show content because
        // current element is abstract, so we clean table content
        if (modes.get(BCOConstants.F_SHOW_ANALYZER)) {
            setVerifyTableItems(null);
        }
        hasAnalyzerError = false;
    }

    private void refreshVerifyView(DecompiledClass result) {
        setVerifyTableItems(result.getTextTable());
        List<Integer> errors = result.getErrorLines();
        if (errors.size() > 0) {
            // TODO this only changes color of status line -
            // but it is possible also to provide useful info here...
            hasAnalyzerError = true;
            // currentErrorMessage = ...
        }
        for (int i = 0; i < errors.size(); ++i) {
            int l = errors.get(i).intValue();
            tableControl.getItem(l).setForeground(errorColor);
        }
        toggleVerifierAction.setEnabled(true);
    }

    /**
     * @param result
     */
    private void updateStatus(DecompiledClass result, int bytecodeOffsetStart,
        int bytecodeOffsetEnd) {
        // clear error messages, if any
        statusLineManager.setErrorMessage(null);
        if (result != null) {
            currentStatusMessage = "Java:"
                + result.getJavaVersion() + " | class size:"
                + result.getClassSize();
            ClassNode classNode = result.getClassNode();
            if(classNode != null && classNode.name != null) {
                setContentDescription(classNode.name);
            }
        } else {
            currentStatusMessage = "";
            setContentDescription("");
        }
        String selectionInfo = "";
        if (bytecodeOffsetStart >= 0) {
            selectionInfo = " | offset:" + bytecodeOffsetStart;
            if (bytecodeOffsetEnd >= 0) {
                selectionInfo += "-" + bytecodeOffsetEnd;
            }
        }
        if (hasAnalyzerError) {
            statusLineManager.setErrorMessage(currentStatusMessage
                + selectionInfo);
        } else {
            statusLineManager.setMessage(currentStatusMessage + selectionInfo);
        }

    }

    public int getBytecodeInstructionAtLine(int line) {
        if (lastDecompiledResult != null) {
            return lastDecompiledResult.getBytecodeInsn(line);
        }
        return -1;
    }

    /**
     * @return IJavaElement which fits in the current selection in java editor
     */
    private IJavaElement getCurrentJavaElement() {
        IJavaElement childEl = null;
        try {
            childEl = JdtUtils.getElementAtOffset(javaInput, currentSelection);
            if (childEl != null) {
                switch (childEl.getElementType()) {
                    case IJavaElement.METHOD :
                    case IJavaElement.FIELD :
                    case IJavaElement.INITIALIZER :
                    case IJavaElement.TYPE :
                        break;
                    case IJavaElement.LOCAL_VARIABLE :
                        childEl = childEl.getAncestor(IJavaElement.METHOD);
                        break;
                    default :
                        childEl = null;
                        break;
                }
            }
        } catch (JavaModelException e) {
            // the exception is mostly occured if java structure was
            // changed and current element is not more exist in model
            // e.g. on rename/delete/move operation.
            // so it is not an error for user, but info for us
            BytecodeOutlinePlugin.log(e, IStatus.INFO);
            setJavaInput(null);
            lastChildElement = null;
        }
        return childEl;
    }

    private void setSelectionInBytecodeView() {
        if (lastDecompiledResult == null) {
            return;
        }

        if (currentSelection.getStartLine() != currentSelection.getEndLine()) {
            setMultiLineSelectionInBytecodeView(currentSelection);
            return;
        }

        int sourceLine = currentSelection.getStartLine() + 1;
        int decompiledLine = lastDecompiledResult.getDecompiledLine(sourceLine);

        if (decompiledLine < 0
            && !modes.get(BCOConstants.F_SHOW_ONLY_SELECTED_ELEMENT)
            && lastChildElement != null) {
            /*
             * May be this is the selection in outline view, if complete class is shown.
             * Because there are no bytecode instructions/offset for method name, we need
             * to find and select first method line. See cr 306011
             */
            DecompiledMethod match = lastDecompiledResult.getBestDecompiledMatch(sourceLine);
            if(match != null){
                // this is relative to method start
                decompiledLine = match.getBestDecompiledLine(sourceLine);
                if(decompiledLine > 0){
                    // convert to class file relative
                    decompiledLine = lastDecompiledResult.getDecompiledLine(match, decompiledLine);
                }
            }
            if(decompiledLine < 0){
                String methodName = JdtUtils.getMethodSignature(lastChildElement);
                if (methodName != null) {
                    decompiledLine = lastDecompiledResult
                        .getDecompiledLine(methodName) - 1;
                }
            }
        }

        if (decompiledLine > 0) {
            try {
                if (modes.get(BCOConstants.F_SHOW_ANALYZER)) {
                    updateVerifierControl4line(decompiledLine);
                    tableControl.setSelection(decompiledLine);
                } else {
                    int lineCount = textControl.getLineCount();
                    if (decompiledLine < lineCount) {
                        int offsetAtLine = textControl
                            .getOffsetAtLine(decompiledLine);
                        int offsetEnd = textControl.getText().indexOf(
                            '\n', offsetAtLine);
                        textControl.setSelection(offsetAtLine, offsetEnd);
                    }
                }
            } catch (IllegalArgumentException e) {
                BytecodeOutlinePlugin.error(null, e);
            }
        } else if (modes.get(BCOConstants.F_SHOW_ANALYZER)) {
            lvtTable.removeAll();
            stackTable.removeAll();
        }
        int bytecodeOffset = lastDecompiledResult
            .getBytecodeOffset(decompiledLine);
        updateStatus(lastDecompiledResult, bytecodeOffset, -1);
    }

    private void setMultiLineSelectionInBytecodeView(
        ITextSelection multiLineSelection) {
        LineRange range = lastDecompiledResult
            .getDecompiledRange(multiLineSelection);
        int firstDecompiledLine = range.startLine;
        if (firstDecompiledLine > 0) {
            try {
                if (modes.get(BCOConstants.F_SHOW_ANALYZER)) {
                    updateVerifierControl4line(firstDecompiledLine);
                    tableControl.setSelection(firstDecompiledLine);
                } else {
                    int lineCount = textControl.getLineCount();
                    if (firstDecompiledLine < lineCount) {
                        int offsetAtLine = textControl
                            .getOffsetAtLine(firstDecompiledLine);
                        int offsetEnd;
                        String text = textControl.getText();
                        if (range.endLine > 0 && range.endLine < lineCount) {
                            offsetEnd = textControl
                                .getOffsetAtLine(range.endLine);
                            offsetEnd = text.indexOf("LINENUMBER", text
                                .indexOf('\n', offsetEnd));
                            if (offsetEnd < 0) {
                                offsetEnd = text.indexOf('\n', offsetEnd);
                            }
                        } else {
                            offsetEnd = text.indexOf('\n', offsetAtLine);
                        }
                        textControl.setSelection(offsetAtLine, offsetEnd);
                    }
                }
            } catch (IllegalArgumentException e) {
                BytecodeOutlinePlugin.error(null, e);
            }
        } else if (modes.get(BCOConstants.F_SHOW_ANALYZER)) {
            lvtTable.removeAll();
            stackTable.removeAll();
        }
        int bytecodeOffsetStart = lastDecompiledResult
            .getBytecodeOffset(firstDecompiledLine);
        int bytecodeOffsetEnd = lastDecompiledResult
            .getBytecodeOffset(range.endLine);
        updateStatus(
            lastDecompiledResult, bytecodeOffsetStart, bytecodeOffsetEnd);
    }

    protected void updateVerifierControl4line(int decompiledLine) {
        String[][][] frame = lastDecompiledResult.getFrameTables(
            decompiledLine, !modes.get(BCOConstants.F_SHOW_RAW_BYTECODE));
        updateVerifierControl(frame);
    }

    protected void updateVerifierControl4insn(int insn) {
        String[][][] frame = lastDecompiledResult.getFrameTablesForInsn(
            insn, !modes.get(BCOConstants.F_SHOW_RAW_BYTECODE));
        updateVerifierControl(frame);
    }

    private void updateVerifierControl(String[][][] frame) {
        lvtTable.removeAll();
        stackTable.removeAll();
        if (frame == null) {
            return;
        }
        for (int i = 0; i < frame[0].length; ++i) {
            if (frame[0][i] != null) {
                new TableItem(lvtTable, SWT.NONE).setText(frame[0][i]);
            }
        }
        for (int i = 0; i < frame[1].length; ++i) {
            if (frame[1][i] != null) {
                new TableItem(stackTable, SWT.NONE).setText(frame[1][i]);
            }
        }

        lvtTable.getColumn(0).pack();
        lvtTable.getColumn(1).pack();
        lvtTable.getColumn(2).pack();
        stackTable.getColumn(0).pack();
        stackTable.getColumn(1).pack();
    }

    protected void setSelectionInJavaEditor(Point selection) {
        if (javaEditor != null && javaEditor.getEditorInput() == null) {
            // editor was closed - we should clean the reference
            javaEditor = null;
            setJavaInput(null);
        }
        if (javaEditor == null || lastDecompiledResult == null) {
            deActivateView();
            return;
        }

        int startDecLine;
        int endDecLine = -1;
        if (modes.get(BCOConstants.F_SHOW_ANALYZER)) {
            startDecLine = tableControl.getSelectionIndex();
            endDecLine = startDecLine;
        } else {
            startDecLine = textControl.getLineAtOffset(selection.x);
            endDecLine = textControl.getLineAtOffset(selection.y);
        }
        int startSourceLine = lastDecompiledResult.getSourceLine(startDecLine);
        int endSourceLine = -1;
        if(endDecLine > 0){
            endSourceLine = lastDecompiledResult.getSourceLine(endDecLine);
        }

        if(endSourceLine < startSourceLine){
            int tmp = startSourceLine;
            startSourceLine = endSourceLine;
            endSourceLine = tmp;
        }

        try {
            if (startSourceLine > 0) {
                IDocument document = javaEditor.getDocumentProvider()
                    .getDocument(javaEditor.getEditorInput());
                try {
                    IRegion lineInfo = document
                        .getLineInformation(startSourceLine - 1);

                    int startOffset = lineInfo.getOffset();
                    int length = lineInfo.getLength();
                    if(endSourceLine > 0){
                        IRegion region = document.getLineInformation(endSourceLine - 1);
                        length = region.getLength() + (region.getOffset() - startOffset);
                    }
                    EclipseUtils.selectInEditor(javaEditor, startOffset, length);
                } catch (BadLocationException e) {
                    // do nothing. This could happens e.g. if editor does not contain
                    // full source code etc, so that line info is not exist in editor
                }
            }
        } catch (Exception e) {
            BytecodeOutlinePlugin.log(e, IStatus.ERROR);
        }

        int bytecodeOffset = lastDecompiledResult
            .getBytecodeOffset(startDecLine);
        updateStatus(lastDecompiledResult, bytecodeOffset, -1);
    }

    // ------------------------------------------------------------------------

    /**
     * check if at least one java editor is open - if not, deactivate me
     * @param checkNewSelection
     */
    protected void checkOpenEditors(boolean checkNewSelection) {
        IEditorReference[] editorReferences = getSite().getPage()
            .getEditorReferences();
        if (editorReferences == null || editorReferences.length == 0) {
            deActivateView();
        } else if (checkNewSelection) {
            IEditorPart activeEditor = EclipseUtils.getActiveEditor();
            if (activeEditor instanceof ITextEditor) {
                ITextSelection selection = EclipseUtils
                    .getSelection(((ITextEditor) activeEditor)
                        .getSelectionProvider());
                handleSelectionChanged(activeEditor, selection);
            } else {
                deActivateView();
            }
        }
    }

    /**
     * @param childEl
     * @return true if java element selection was changed (means, that previous selection
     * do not match to the given element)
     */
    private boolean isSelectedElementChanged(IJavaElement childEl) {

        if (lastChildElement == null && childEl == null) {
            // no selected child before - and no new selection now => no changes
            return false;
        }

        if (modes.get(BCOConstants.F_SHOW_ONLY_SELECTED_ELEMENT)) {
            if (lastChildElement == null
                || (lastChildElement != null && !lastChildElement
                    .equals(childEl))) {
                return true;
            }
        }

        /*
         * the check if we changed from inner class to outer class or vice versa
         */
        if (lastChildElement != null && childEl != null) {
            IType newEnclosingType = JdtUtils.getEnclosingType(childEl);
            IType oldEnclosingType = JdtUtils
                .getEnclosingType(lastChildElement);
            return newEnclosingType == null
                || !newEnclosingType.equals(oldEnclosingType);
        }
        return false;
    }

    /**
     * @param childEl
     * @return return null if type is not known or bytecode is not written or cannot be
     * found
     */
    private DecompiledClass decompileBytecode(IJavaElement childEl) {
        // check here for inner classes too
        IJavaElement type = JdtUtils.getEnclosingType(childEl);
        if (type == null) {
            type = javaInput;
        }
        if (type == null) {
            return null;
        }
        InputStream is = JdtUtils.createInputStream(type);
        if (is == null) {
            return null;
        }
        DecompiledClass decompiledClass = null;
        int available = 0;
        try {
            ClassLoader cl = null;
            if (modes.get(BCOConstants.F_SHOW_ANALYZER)) {
                cl = JdtUtils.getClassLoader(type);
            }

            String fieldName = null;
            String methodName = null;
            /*
             * find out, which name we should use for selected element
             */
            if (modes.get(BCOConstants.F_SHOW_ONLY_SELECTED_ELEMENT)
                && childEl != null) {
                if (childEl.getElementType() == IJavaElement.FIELD) {
                    fieldName = childEl.getElementName();
                } else {
                    methodName = JdtUtils.getMethodSignature(childEl);
                }
            }
            available = is.available();
            decompiledClass = DecompilerHelper.getDecompiledClass(
                is, new DecompilerOptions(fieldName, methodName, modes, cl));
        } catch (Exception e) {
            try {
                // check if compilation unit is ok - then this is the user problem
                if (type.isStructureKnown()) {
                    BytecodeOutlinePlugin.error("Cannot decompile: " + type, e);
                } else {
                    BytecodeOutlinePlugin.log(e, IStatus.ERROR);
                }
            } catch (JavaModelException e1) {
                // this is compilation problem - don't show the message
                BytecodeOutlinePlugin.log(e1, IStatus.WARNING);
            }
        } catch (UnsupportedClassVersionError e) {
            BytecodeOutlinePlugin.error("Cannot decompile: " + type
                + ". Error was caused by attempt to "
                + "load a class compiled with the Java version which is not "
                + "supported by the current JVM. ", e);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                BytecodeOutlinePlugin.log(e, IStatus.WARNING);
            }
        }
        // remember class file size to show it later in UI
        if (decompiledClass != null) {
            decompiledClass.setClassSize(available);
        }
        return decompiledClass;
    }

    private void setVerifyTableItems(String[][] items) {
        tableControl.removeAll();
        if (items != null) {
            for (int i = 0; i < items.length; ++i) {
                TableItem item = new TableItem(tableControl, SWT.NONE);
                for (int j = 0; j < items[i].length; ++j) {
                    String s = items[i][j];
                    if (s.endsWith("\n")) {
                        s = s.substring(0, s.length() - 1);
                        // this is the "cookie" for the bytecode reference, which could be
                        // mapped later to the sourcecode line on selection event in the
                        // table
                        item.setData(Integer.valueOf(i));
                    }
                    item.setText(j, s);
                }
            }
            tableControl.getColumn(0).pack();
            tableControl.getColumn(1).pack();
            tableControl.getColumn(2).pack();
            tableControl.getColumn(3).pack();
            tableControl.getColumn(4).pack();
        }
    }

    @Override
    public Object getAdapter(Class adapter) {
        if (IFindReplaceTarget.class.equals(adapter)) {
            return textViewer.getFindReplaceTarget();
        }
        if (Widget.class.equals(adapter)) {
            return textViewer.getTextWidget();
        }
        if (TextViewer.class.equals(adapter)) {
            return textViewer;
        }
        return super.getAdapter(adapter);
    }

    /**
     * Configures an action for key bindings.
     * @param actionBars action bars for this page
     * @param actionID action definition id
     * @param action associated action
     */
    protected void setGlobalAction(IActionBars actionBars, String actionID,
        IAction action) {
        globalActions.put(actionID, action);
        actionBars.setGlobalActionHandler(actionID, action);
    }

    /**
     * Updates the global action with the given id
     * @param actionId action definition id
     */
    protected void updateAction(String actionId) {
        IAction action = globalActions.get(actionId);
        if (action instanceof IUpdate) {
            ((IUpdate) action).update();
        }
    }

    protected void createTextActions() {
        IActionBars actionBars = getViewSite().getActionBars();
        TextViewerAction action = new TextViewerAction(
            textViewer, ITextOperationTarget.SELECT_ALL);

        action.configureAction(
            BytecodeOutlinePlugin.getResourceString(NLS_PREFIX
                + "select_all.label"), BytecodeOutlinePlugin
                .getResourceString(NLS_PREFIX + "select_all.tooltip"),
            BytecodeOutlinePlugin.getResourceString(NLS_PREFIX
                + "select_all.description"));
        setGlobalAction(actionBars, ActionFactory.SELECT_ALL.getId(), action);

        action = new TextViewerAction(textViewer, ITextOperationTarget.COPY);
        action.configureAction(
            BytecodeOutlinePlugin.getResourceString(NLS_PREFIX + "copy.label"),
            BytecodeOutlinePlugin
                .getResourceString(NLS_PREFIX + "copy.tooltip"),
            BytecodeOutlinePlugin.getResourceString(NLS_PREFIX
                + "copy.description"));
        action.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages()
            .getImageDescriptor(ISharedImages.IMG_TOOL_COPY));
        action.setActionDefinitionId(IWorkbenchCommandConstants.EDIT_COPY);
        setGlobalAction(actionBars, ActionFactory.COPY.getId(), action);

        ResourceBundle bundle = BytecodeOutlinePlugin.getDefault()
            .getResourceBundle();

        setGlobalAction(
            actionBars, ActionFactory.FIND.getId(), new FindReplaceAction(
                bundle, NLS_PREFIX + "find_replace.", this)); //$NON-NLS-1$

        selectionActions.add(ActionFactory.COPY.getId());
        selectionActions.add(ActionFactory.FIND.getId());

        actionBars.updateActionBars();
    }

    // orientation

    private void setOrientation(int orientation) {
        if (verifyControl == null || verifyControl.isDisposed()) {
            return;
        }

        boolean horizontal = orientation == VIEW_ORIENTATION_HORIZONTAL;
        verifyControl.setOrientation(horizontal
            ? SWT.HORIZONTAL
            : SWT.VERTICAL);

        for (int i = 0; i < toggleOrientationActions.length; ++i) {
            toggleOrientationActions[i]
                .setChecked(orientation == toggleOrientationActions[i]
                    .getOrientation());
        }

        currentOrientation = orientation;
        // GridLayout layout= (GridLayout) fCounterComposite.getLayout();
        // setCounterColumns(layout);
        stackComposite.getParent().layout();
    }

    protected void computeOrientation() {
        if (orientation != VIEW_ORIENTATION_AUTOMATIC) {
            currentOrientation = orientation;
            setOrientation(currentOrientation);
        } else {
            Point size = stackComposite.getParent().getSize();
            if (size.x != 0 && size.y != 0) {
                setOrientation(size.x > size.y
                    ? VIEW_ORIENTATION_HORIZONTAL
                    : VIEW_ORIENTATION_VERTICAL);
            }
        }
    }

    /**
     * Set the bit with given index to given value and remembers it in the preferences
     * @param bitIndex
     * @param value
     */
    protected void setMode(int bitIndex, boolean value) {
        modes.set(bitIndex, value);
    }

    protected void toggleVerifyMode(final IMenuManager mmanager,
        boolean showAnalyzer) {
        setMode(BCOConstants.F_SHOW_ANALYZER, showAnalyzer);
        if (modes.get(BCOConstants.F_SHOW_ANALYZER)) {
            ((StackLayout) stackComposite.getLayout()).topControl = verifyControl;
            viewSelectionProvider
                .setCurrentSelectionProvider(tableControlViewer);
        } else {
            ((StackLayout) stackComposite.getLayout()).topControl = textControl;
            viewSelectionProvider.setCurrentSelectionProvider(textViewer);
        }
        stackComposite.layout();

        for (int i = 0; i < toggleOrientationActions.length; ++i) {
            toggleOrientationActions[i].setEnabled(showAnalyzer);
        }
        mmanager.markDirty();
        mmanager.update();
    }

    private class ToggleOrientationAction extends Action {

        private final int actionOrientation;

        public ToggleOrientationAction(BytecodeOutlineView v, int orientation) {
            super("", AS_RADIO_BUTTON); //$NON-NLS-1$

            String symbolicName = BytecodeOutlinePlugin.getDefault()
                .getBundle().getSymbolicName();
            if (orientation == VIEW_ORIENTATION_HORIZONTAL) {
                setText(BytecodeOutlinePlugin.getResourceString(NLS_PREFIX
                    + "toggle.horizontal.label")); //$NON-NLS-1$
                setImageDescriptor(AbstractUIPlugin.imageDescriptorFromPlugin(
                    symbolicName, "icons/th_horizontal.gif")); //$NON-NLS-1$
            } else if (orientation == VIEW_ORIENTATION_VERTICAL) {
                setText(BytecodeOutlinePlugin.getResourceString(NLS_PREFIX
                    + "toggle.vertical.label")); //$NON-NLS-1$
                setImageDescriptor(AbstractUIPlugin.imageDescriptorFromPlugin(
                    symbolicName, "icons/th_vertical.gif")); //$NON-NLS-1$
            } else if (orientation == VIEW_ORIENTATION_AUTOMATIC) {
                setText(BytecodeOutlinePlugin.getResourceString(NLS_PREFIX
                    + "toggle.automatic.label")); //$NON-NLS-1$
                setImageDescriptor(AbstractUIPlugin.imageDescriptorFromPlugin(
                    symbolicName, "icons/th_automatic.gif")); //$NON-NLS-1$
            }
            actionOrientation = orientation;
            // WorkbenchHelp.setHelp(this,
            // IJUnitHelpContextIds.RESULTS_VIEW_TOGGLE_ORIENTATION_ACTION);
        }

        public int getOrientation() {
            return actionOrientation;
        }

        @Override
        public void run() {
            if (isChecked()) {
                orientation = actionOrientation;
                computeOrientation();
            }
        }
    }

}
