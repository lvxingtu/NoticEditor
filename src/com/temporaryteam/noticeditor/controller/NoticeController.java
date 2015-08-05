package com.temporaryteam.noticeditor.controller;

import com.temporaryteam.noticeditor.model.NoticeTreeItem;
import org.json.JSONObject;
import org.json.JSONException;

import org.pegdown.PegDownProcessor;
import static org.pegdown.Extensions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javafx.util.Callback;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.scene.control.*;
import javafx.scene.web.WebView;
import javafx.scene.web.WebEngine;

import com.temporaryteam.noticeditor.Main;
import com.temporaryteam.noticeditor.io.IOUtil;
import com.temporaryteam.noticeditor.model.PreviewStyles;
import com.temporaryteam.noticeditor.view.EditNoticeTreeCell;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.stage.DirectoryChooser;
import jfx.messagebox.MessageBox;

public class NoticeController {

	@FXML
	private SplitPane mainPanel;

	@FXML
	private SplitPane editorPanel;

	@FXML
	private TextArea noticeArea;

	@FXML
	private WebView viewer;

	@FXML
	private MenuItem addBranchItem, addNoticeItem, deleteItem;

	@FXML
	private CheckMenuItem wordWrapItem;

	@FXML
	private Menu previewStyleMenu;

	@FXML
	private TreeView<String> noticeTree;

	private Main main;
	private FileChooser fileChooser;
	private DirectoryChooser dirChooser;
	private WebEngine engine;
	private PegDownProcessor processor;
	private NoticeTreeItem currentTreeItem;
	private EditNoticeTreeCell cell;
	private File fileSaved;

	public NoticeController() {
		fileChooser = new FileChooser();
		fileChooser.getExtensionFilters().addAll(
				new ExtensionFilter("Text files", "*.txt"),
				new ExtensionFilter("All files", "*"));
		dirChooser = new DirectoryChooser();
		dirChooser.setTitle("Select folder to save");
		processor = new PegDownProcessor(AUTOLINKS | TABLES | FENCED_CODE_BLOCKS);
	}

	/**
	 * Initializes the controller class.
	 */
	@FXML
	private void initialize() {
		noticeArea.setText("help");
		noticeTree.setShowRoot(false);
		engine = viewer.getEngine();

		// Set preview styles menu items
		ToggleGroup previewStyleGroup = new ToggleGroup();
		for (PreviewStyles style : PreviewStyles.values()) {
			final String cssPath = style.getCssPath();
			RadioMenuItem item = new RadioMenuItem(style.getName());
			item.setToggleGroup(previewStyleGroup);
			if (cssPath == null) {
				item.setSelected(true);
			}
			item.setOnAction(new EventHandler<ActionEvent>() {
				public void handle(ActionEvent e) {
					String path = cssPath;
					if (path != null) {
						path = getClass().getResource(path).toExternalForm();
					}
					engine.setUserStyleSheetLocation(path);
				}
			});
			previewStyleMenu.getItems().add(item);
		}

		rebuild("help");
		final NoticeController controller = this;
		noticeTree.setShowRoot(false);
		noticeTree.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		noticeTree.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<TreeItem<String>>() {
			@Override
			public void changed(ObservableValue<? extends TreeItem<String>> observable, TreeItem<String> oldValue, TreeItem<String> newValue) {
				if (newValue == null) {
					return;
				}
				currentTreeItem = (NoticeTreeItem) newValue;
				noticeArea.setEditable(currentTreeItem.isLeaf());
				if (currentTreeItem.isLeaf()) {
					open(currentTreeItem);
				}
			}
		});
		noticeTree.setCellFactory(new Callback<TreeView<String>, TreeCell<String>>() {
			@Override
			public TreeCell<String> call(TreeView<String> p) {
				cell = new EditNoticeTreeCell();
				cell.setController(controller);
				return cell;
			}
		});

		engine.loadContent(noticeArea.getText());
		noticeArea.textProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
				engine.loadContent(operate(newValue));
				currentTreeItem.changeContent(newValue);
			}
		});
		noticeArea.wrapTextProperty().bind(wordWrapItem.selectedProperty());
	}

	public MenuItem getAddBranchItem() {
		return addBranchItem;
	}

	public MenuItem getAddNoticeItem() {
		return addNoticeItem;
	}

	public MenuItem getDeleteItem() {
		return deleteItem;
	}

	public NoticeTreeItem getCurrentTreeItem() {
		return currentTreeItem;
	}

	/**
	 * Save item as HTML pages. Root item was saved to index.html
	 *
	 * @param item node to recursively save
	 * @param file file to save
	 */
	public void exportToHtmlPages(NoticeTreeItem<String> item, File file) throws IOException {
		IOUtil.writeContent(file, item.toHTML(processor));
		if (item.isBranch()) {
			for (Object obj : item.getChildren()) {
				NoticeTreeItem child = (NoticeTreeItem) obj;
				exportToHtmlPages(child, new File(file.getParent(), child.getId() + ".html"));
			}
		}
	}

	/**
	 * Write node in filesystem
	 */
	private void writeFSNode(NoticeTreeItem item, File dir) throws IOException {
		String title = item.getTitle();
		System.out.println("In " + item.getTitle() + " with title " + title);
		if (item.isBranch()) {
			for (Object child : item.getChildren()) {
				File newDir = new File(dir.getPath() + "/" + title);
				if (newDir.exists()) {
					newDir.delete();
				}
				newDir.mkdir();
				writeFSNode((NoticeTreeItem) child, newDir);
			}
		} else {
			File toWrite = new File(dir.getPath() + "/" + title + ".md");
			IOUtil.writeContent(toWrite, item.getContent());
		}
		System.out.println("Exit");
	}

	/**
	 * Rebuild tree
	 */
	public void rebuild(String defaultNoticeContent) {
		NoticeTreeItem rootItem = new NoticeTreeItem("Root");
		currentTreeItem = new NoticeTreeItem("Default notice", defaultNoticeContent);
		rootItem.getChildren().add(currentTreeItem);
		noticeTree.setRoot(rootItem);
	}

	/**
	 * Open notice in TextArea
	 */
	public void open(NoticeTreeItem notice) {
		noticeArea.setText(notice.getContent());
	}

	/**
	 * Method for operate with markdown
	 */
	private String operate(String source) {
		return processor.markdownToHtml(source);
	}

	/**
	 * Handler
	 */
	@FXML
	private void handleContextMenu(ActionEvent event) {
		cell.handleContextMenu(event);
	}

	@FXML
	private void handleNew(ActionEvent event) {
		noticeArea.setText("help");
		rebuild("help");
		fileSaved = null;
	}

	@FXML
	private void handleOpen(ActionEvent event) {
		if (fileSaved != null) {
			fileChooser.setInitialDirectory(new File(fileSaved.getParent()));
		}
		fileSaved = fileChooser.showOpenDialog(main.getPrimaryStage());
		if (fileSaved == null) {
			return;
		}
		try {
			JSONObject json = new JSONObject(IOUtil.readContent(fileSaved));
			currentTreeItem = new NoticeTreeItem(json);
			noticeArea.setText("");
			noticeTree.setRoot(currentTreeItem);
		} catch (IOException | JSONException e) {
			e.printStackTrace();
		}
	}

	@FXML
	private void handleSave(ActionEvent event) {
		if (fileSaved == null) {
			fileChooser.setTitle("Save notice");
			fileSaved = fileChooser.showSaveDialog(main.getPrimaryStage());
			if (fileSaved == null) {
				return;
			}
		}
		try {
			IOUtil.writeJson(fileSaved, ((NoticeTreeItem) noticeTree.getRoot()).toJson());
		} catch (IOException | JSONException ioe) {
		}
	}

	@FXML
	private void handleSaveAs(ActionEvent event) {
		if (fileSaved != null) {
			fileChooser.setInitialDirectory(new File(fileSaved.getParent()));
		}
		fileChooser.setTitle("Save notice");
		fileSaved = fileChooser.showSaveDialog(main.getPrimaryStage());
		if (fileSaved == null) {
			return;
		}

		try {
			IOUtil.writeJson(fileSaved, ((NoticeTreeItem) noticeTree.getRoot()).toJson());
		} catch (IOException | JSONException e) {
			e.printStackTrace();
		}
	}

	@FXML
	private void handleSaveToZip(ActionEvent event) {
		fileChooser.setTitle("Save notice as zip archive");
		File destFile = fileChooser.showSaveDialog(main.getPrimaryStage());
		if (destFile == null) {
			return;
		}
		try {
			File temporaryDir = Files.createTempDirectory("noticeditor").toFile();
			writeFSNode((NoticeTreeItem) noticeTree.getRoot(), temporaryDir);
			IOUtil.pack(temporaryDir, destFile.getPath());
			IOUtil.removeDirectory(temporaryDir);
		} catch (IOException ioe) {
		}
	}

	@FXML
	private void handleExportHtml(ActionEvent event) {
		dirChooser.setTitle("Select dir to save HTML files");
		File destDir = dirChooser.showDialog(main.getPrimaryStage());
		if (destDir == null) {
			return;
		}
		File indexFile = new File(destDir, "index.html");
		try {
			exportToHtmlPages((NoticeTreeItem) noticeTree.getRoot(), indexFile);
			MessageBox.show(main.getPrimaryStage(), "Export success!", "", MessageBox.OK);
		} catch (IOException ioe) {
		}
	}

	@FXML
	private void handleExit(ActionEvent event) {
		Platform.exit();
	}

	@FXML
	private void handleSwitchOrientation(ActionEvent event) {
		editorPanel.setOrientation(editorPanel.getOrientation() == Orientation.HORIZONTAL
				? Orientation.VERTICAL : Orientation.HORIZONTAL);
	}

	@FXML
	private void handleAbout(ActionEvent event) {

	}

	/**
	 * Sets reference to Main class
	 */
	public void setMain(Main main) {
		this.main = main;
	}

}