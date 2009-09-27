package tac.gui.atlastree;

import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.dnd.Autoscroll;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.apache.log4j.Logger;

import tac.gui.MainGUI;
import tac.gui.mapview.MultiMapSelectionLayer;
import tac.gui.mapview.PreviewMap;
import tac.program.MapSelection;
import tac.program.interfaces.AtlasInterface;
import tac.program.interfaces.AtlasObject;
import tac.program.interfaces.CapabilityDeletable;
import tac.program.interfaces.MapInterface;
import tac.program.interfaces.ToolTipProvider;
import tac.program.model.Atlas;
import tac.program.model.AtlasTreeModel;
import tac.program.model.Profile;
import tac.program.model.TileImageParameters;
import tac.utilities.TACExceptionHandler;

public class JAtlasTree extends JTree implements Autoscroll {

	private static final long serialVersionUID = 1L;
	private static final int margin = 12;

	private static final String MSG_ATLAS_VERSION_MISMATCH = ""
			+ "The loaded atlas belongs to an older version TrekBuddy Atlas Creator. "
			+ "This old version \nused a somehow different atlas profile format "
			+ "which is incompatible to this version.\n\n"
			+ "It is recommended to clear the loaded atlas and delete the affected profile.\n"
			+ "Otherwise various exceptions may be thrown while working with this atlas.";

	private static final String MSG_ATLAS_DATA_CHECK_FAILED = ""
			+ "At least one problem was detected while loading the saved atlas profile.\n"
			+ "Usually this indicates that the profile file is inconsistent "
			+ "or the file format \n" + "has changed.\n\n"
			+ "It is recommended to clear the loaded atlas and delete the affected profile.\n"
			+ "Otherwise various exceptions may be thrown while working with this atlas.";

	private static final String MSG_ATLAS_EMPTY = "Atlas is empty - "
			+ "please add at least one selection to atlas content.";

	private static final String ACTION_DELETE_NODE = "DELETE_NODE";

	private static final Logger log = Logger.getLogger(JAtlasTree.class);

	private AtlasTreeModel treeModel;

	private PreviewMap mapView;

	protected NodeRenderer nodeRenderer;

	protected String defaultToolTiptext;

	protected KeyStroke deleteNodeKS;

	protected DragDropController ddc;

	public JAtlasTree(PreviewMap mapView) {
		super(new AtlasTreeModel());
		if (mapView == null)
			throw new NullPointerException("MapView parameter is null");
		this.mapView = mapView;
		getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		ddc = new DragDropController(this);
		treeModel = (AtlasTreeModel) getModel();
		// setRootVisible(false);
		setShowsRootHandles(true);
		nodeRenderer = new NodeRenderer();
		setCellRenderer(nodeRenderer);
		setCellEditor(new NodeEditor(this));
		setToolTipText("");
		defaultToolTiptext = "<html>Use context menu of the entries to see all available commands.</html>";
		setAutoscrolls(true);
		addMouseListener(new MouseController(this));

		InputMap inputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap actionMap = getActionMap();

		// map moving
		inputMap.put(deleteNodeKS = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
				ACTION_DELETE_NODE);
		actionMap.put(ACTION_DELETE_NODE, new AbstractAction("Delete") {

			public void actionPerformed(ActionEvent e) {
				deleteSelectedNode();
				JAtlasTree.this.mapView.repaint();
			}

		});

	}

	public boolean testAtlasContentValid() {
		if (getAtlas().calculateTilesToDownload() == 0) {
			JOptionPane.showMessageDialog(null, "<html>" + MSG_ATLAS_EMPTY + "</html>",
					"Error - atlas has no content", JOptionPane.ERROR_MESSAGE);
			return false;
		}
		return true;
	}

	@Override
	public String getToolTipText(MouseEvent event) {
		if (getRowForLocation(event.getX(), event.getY()) == -1)
			return defaultToolTiptext;
		TreePath curPath = getPathForLocation(event.getX(), event.getY());
		Object o = curPath.getLastPathComponent();
		if (o == null || !(o instanceof ToolTipProvider))
			return null;
		return ((ToolTipProvider) o).getToolTip();
	}

	@Override
	public boolean isPathEditable(TreePath path) {
		return super.isPathEditable(path) && (path.getLastPathComponent() instanceof AtlasObject);
	}

	public AtlasTreeModel getTreeModel() {
		return treeModel;
	}

	public void clearAtlas() {
		log.debug("Resetting atlas tree model");
		Atlas newAtlas = Atlas.newInstance();
		newAtlas.setName(MainGUI.getMainGUI().getUserText());
		treeModel.setAtlas(newAtlas);
		mapView.mapLayers.clear();
		mapView.repaint();
	}

	public void deleteSelectedNode() {
		TreePath path = getSelectionPath();
		if (path == null)
			return;
		TreeNode selected = (TreeNode) path.getLastPathComponent();
		int[] selectedRows = getSelectionRows();

		if (!(selected instanceof CapabilityDeletable))
			return;
		treeModel.notifyNodeDelete(selected);
		((CapabilityDeletable) selected).delete();

		int selRow = Math.min(selectedRows[0], getRowCount() - 1);
		TreePath path1 = path.getParentPath();
		TreePath path2 = getPathForRow(selRow).getParentPath();
		if (path1 != path2) {
			// next row belongs to different parent node -> we select parent
			// node instead
			setSelectionPath(path1);
		} else {
			setSelectionRow(selRow);
			scrollRowToVisible(selRow);
		}
	}

	public AtlasInterface getAtlas() {
		return treeModel.getAtlas();
	}

	public boolean load(Profile profile) {
		try {
			treeModel.load(profile);
			if (treeModel.getAtlas() instanceof Atlas) {
				Atlas atlas = (Atlas) treeModel.getAtlas();
				if (atlas.getVersion() < Atlas.CURRENT_ATLAS_VERSION) {
					JOptionPane.showMessageDialog(null, MSG_ATLAS_VERSION_MISMATCH,
							"Outdated atlas version", JOptionPane.WARNING_MESSAGE);
					return true;
				}
			}
			boolean problemsDetected = Profile.checkAtlas(treeModel.getAtlas());
			if (problemsDetected) {
				JOptionPane.showMessageDialog(null, MSG_ATLAS_DATA_CHECK_FAILED,
						"Atlas loading problem", JOptionPane.WARNING_MESSAGE);
			}
			return true;
		} catch (Exception e) {
			TACExceptionHandler.processException(e);
			return false;
		}
	}

	public boolean save(Profile profile) {
		try {
			treeModel.save(profile);
			return true;
		} catch (Exception e) {
			TACExceptionHandler.processException(e);
			return false;
		}
	}

	protected void showNodePopupMenu(MouseEvent event) {
		JPopupMenu pm = new JPopupMenu();
		final TreePath selPath = getPathForLocation(event.getX(), event.getY());
		setSelectionPath(selPath);
		JMenuItem mi = null;
		if (selPath != null) {
			// not clicked on empty area
			final Object o = selPath.getLastPathComponent();
			if (o == null)
				return;
			if (o instanceof ToolTipProvider) {
				mi = new JMenuItem("Show item details");
				mi.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						ToolTipProvider ttp = (ToolTipProvider) o;
						JOptionPane.showMessageDialog(MainGUI.getMainGUI(), ttp.getToolTip());
					}
				});
				pm.add(mi);
			}
			if (o instanceof AtlasObject) {
				mi = new JMenuItem("Display map areas");
				mi.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						mapView.setSelectionByTileCoordinate(null, null, false);
						mapView.mapLayers.clear();
						mapView.mapLayers.add(new MultiMapSelectionLayer((AtlasObject) o));
						mapView.repaint();
					}
				});
				pm.add(mi);
			}
			if (o instanceof MapInterface) {
				mi = new JMenuItem("Select map area");
				mi.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						MapInterface map = (MapInterface) o;
						mapView.setMapSource(map.getMapSource());
						mapView.setSelectionByTileCoordinate(map.getZoom(), map
								.getMinTileCoordinate(), map.getMaxTileCoordinate(), true);
					}
				});
				pm.add(mi);
				mi = new JMenuItem("Zoom to");
				mi.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						MapInterface map = (MapInterface) o;
						MapSelection ms = new MapSelection(map);
						mapView.setMapSource(map.getMapSource());
						mapView.zoomToSelection(ms, true);
						mapView.setSelectionByTileCoordinate(map.getZoom(), map
								.getMinTileCoordinate(), map.getMaxTileCoordinate(), true);
					}
				});
				pm.add(mi);
			}
			if (o instanceof AtlasObject) {
				mi = new JMenuItem("Rename");
				mi.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						JAtlasTree.this.startEditingAtPath(selPath);
					}
				});
				pm.add(mi);
				mi = new JMenuItem("Apply tile processing options");
				mi.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						AtlasObject atlasObject = (AtlasObject) o;
						TileImageParameters p = MainGUI.getMainGUI()
								.getSelectedTileImageParameters();
						applyTileImageParameters(atlasObject, p);
					}
				});
				pm.add(mi);
			}
			if (o instanceof CapabilityDeletable) {
				pm.addSeparator();
				mi = new JMenuItem(getActionMap().get(ACTION_DELETE_NODE));
				mi.setAccelerator(deleteNodeKS);
				pm.add(mi);
			}
		}
		if (pm.getComponentCount() > 0)
			pm.addSeparator();
		mi = new JMenuItem("Clear atlas");
		mi.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				clearAtlas();
			}
		});
		pm.add(mi);
		pm.show(this, event.getX(), event.getY());
	}

	protected void applyTileImageParameters(Object o, TileImageParameters p) {
		if (o instanceof Iterable<?>) {
			Iterable<?> it = (Iterable<?>) o;
			for (Object ao : it) {
				applyTileImageParameters(ao, p);
			}
		} else if (o instanceof MapInterface) {
			((MapInterface) o).setParameters(p);
		}
	}

	protected void selectElementOnMap(Object o) {
		if (o instanceof MapInterface) {
			MapInterface map = (MapInterface) o;
			mapView.setMapSource(map.getMapSource());
			mapView.setSelectionByTileCoordinate(map.getZoom(), map.getMinTileCoordinate(), map
					.getMaxTileCoordinate(), true);
		}
	}

	public void autoscroll(Point cursorLocn) {
		int realrow = getRowForLocation(cursorLocn.x, cursorLocn.y);
		Rectangle outer = getBounds();
		realrow = (cursorLocn.y + outer.y <= margin ? realrow < 1 ? 0 : realrow - 1
				: realrow < getRowCount() - 1 ? realrow + 1 : realrow);
		scrollRowToVisible(realrow);
	}

	public Insets getAutoscrollInsets() {
		Rectangle outer = getBounds();
		Rectangle inner = getParent().getBounds();
		return new Insets(inner.y - outer.y + margin, inner.x - outer.x + margin, outer.height
				- inner.height - inner.y + outer.y + margin, outer.width - inner.width - inner.x
				+ outer.x + margin);
	}

}