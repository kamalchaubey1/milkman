package milkman.plugin.test.editor;

import com.jfoenix.controls.JFXTreeView;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.TransferMode;
import lombok.extern.slf4j.Slf4j;
import milkman.domain.RequestContainer;
import milkman.plugin.test.domain.TestAspect;
import milkman.ui.plugin.PluginRequestExecutor;
import milkman.ui.plugin.RequestAspectEditor;
import milkman.ui.plugin.RequestExecutorAware;
import milkman.utils.javafx.MappedList;
import milkman.utils.javafx.SettableTreeItem;

import static milkman.utils.fxml.FxmlBuilder.*;
import static milkman.utils.javafx.DndUtil.JAVA_FORMAT;
import static milkman.utils.javafx.DndUtil.deserialize;

@Slf4j
public class TestAspectEditor implements RequestAspectEditor, RequestExecutorAware {

	JFXTreeView<Node> requestSequence;
	private SettableTreeItem<Node> root;
	private ObservableList<TestAspect.TestDetails> requests;
	private ObservableList<TreeItem<Node>> mappedRequests;
	private PluginRequestExecutor requestExecutor;
	private VboxExt requestDetails;


	@Override
	public Tab getRoot(RequestContainer request) {
		TestAspect testAspect = request.getAspect(TestAspect.class)
				.orElseThrow(() -> new IllegalArgumentException("missing test aspect"));


		requests = FXCollections.observableList(testAspect.getRequests());
		mappedRequests = new MappedList<>(requests, reqDetails -> {
			var treeItem = new SettableTreeItem<Node>();
			treeItem.setValue(requestDetailsToNode(reqDetails));
			return treeItem;
		});

		root = new SettableTreeItem<Node>();
		root.setChildren(mappedRequests);



		var editor = new TestAspectEditorFxml(this);


		setupDnD(requestSequence, testAspect);
		requestSequence.getSelectionModel().selectedIndexProperty().addListener((obs, old, newValue) -> {
			if (newValue != null && !newValue.equals(old)){
				var testDetails = requests.get(newValue.intValue());
				requestExecutor.getDetails(testDetails.getId())
						.ifPresent(reqContainer -> this.onRequestSelected(reqContainer, testDetails));
			}
		});


		if (requests.size() > 0){
			//we have to do this in order for the component to refresh, otherwise, an empty treeView is rendered
			//bug in javafx?
			requests.add(0, requests.remove(0));
		}

		return editor;
	}


	private void setupDnD(JFXTreeView<Node> target, TestAspect testAspect) {
		target.setOnDragOver( e -> {
			if (e.getGestureSource() != target
			&& e.getDragboard().hasContent(JAVA_FORMAT)) {
				e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
			}
			e.consume();
		});

		target.setOnDragDropped(e -> {
			if (e.getGestureSource() != target
					&& e.getDragboard().hasContent(JAVA_FORMAT)) {
				try {
					var content = deserialize((String) e.getDragboard().getContent(JAVA_FORMAT), RequestContainer.class);
					requests.add(new TestAspect.TestDetails(content.getId(), false));
					testAspect.setDirty(true);
					e.setDropCompleted(true);
				} catch (Exception ex) {
					log.error("failed dnd operation", ex);
					e.setDropCompleted(false);
				}
				e.consume();
			}
		});
	}

	private HboxExt requestDetailsToNode(TestAspect.TestDetails requestDetails) {
		var requestName = requestExecutor.getDetails(requestDetails.getId()).get().getName();
		var label = new Label(requestName);
		if (requestDetails.isSkip()){
			label.setStyle("-fx-text-fill: grey;");
		}
		return hbox(label);
	}

	@Override
	public boolean canHandleAspect(RequestContainer request) {
		return request.getAspect(TestAspect.class).isPresent();
	}

	@Override
	public void setRequestExecutor(PluginRequestExecutor executor) {
		this.requestExecutor = executor;
	}


	private void onRequestSelected(RequestContainer requestContainer, TestAspect.TestDetails details) {
		requestDetails.getChildren().clear();
		requestDetails.add(new Label(requestContainer.getName()));

		var cbSkip = new CheckBox("skip test");
		cbSkip.setSelected(details.isSkip());
		cbSkip.selectedProperty().addListener((obs, o, n) -> {
			if (n != null) {
				details.setSkip(n);
			}
		});
		requestDetails.add(cbSkip);
	}

	private void moveUp() {
		var selectedIndex = requestSequence.getSelectionModel().getSelectedIndex();
		if (selectedIndex > 0) {
			requests.add(selectedIndex-1, requests.remove(selectedIndex));
			requestSequence.getSelectionModel().select(selectedIndex-1);
		}
	}

	private void moveDown() {
		var selectedIndex = requestSequence.getSelectionModel().getSelectedIndex();
		if (selectedIndex >= 0 && selectedIndex < requests.size()-1) {
			requests.add(selectedIndex+1, requests.remove(selectedIndex));
			requestSequence.getSelectionModel().select(selectedIndex+1);
		}
	}

	private void delete() {
		var selectedIndex = requestSequence.getSelectionModel().getSelectedIndex();
		if (selectedIndex >= 0) {
			requests.remove(selectedIndex);
		}
	}


	public static class TestAspectEditorFxml extends Tab {
		private final TestAspectEditor controller;

		public TestAspectEditorFxml(TestAspectEditor controller) {
			super("Tests");
			this.controller = controller;
			controller.requestSequence = new JFXTreeView<>();
			controller.requestSequence.setShowRoot(false);
//			controller.requestSequence.setCellFactory(new DnDCellFactory());
			controller.requestSequence.setRoot(controller.root);

			var splitPane = new SplitPane();
			splitPane.setDividerPositions(0.3);

			var requestControls = vbox("testrequest-controls");

			requestControls.add(button("testrequest-up", icon(FontAwesomeIcon.CHEVRON_UP), controller::moveUp));
			requestControls.add(button("testrequest-down", icon(FontAwesomeIcon.CHEVRON_DOWN), controller::moveDown));
			requestControls.add(button("testrequest-down", icon(FontAwesomeIcon.TRASH), controller::delete));
			splitPane.getItems().add(hbox(controller.requestSequence, requestControls));

			controller.requestDetails = vbox();
			controller.requestDetails.getStyleClass().add("generic-content-pane");
			splitPane.getItems().add(controller.requestDetails);

			setContent(splitPane);
		}
	}


}
