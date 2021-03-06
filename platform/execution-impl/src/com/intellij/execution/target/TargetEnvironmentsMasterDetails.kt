// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.ui.CommonActionsPanel
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.IconUtil
import com.intellij.util.PlatformIcons
import com.intellij.util.containers.toArray
import com.intellij.util.text.UniqueNameGenerator
import com.intellij.util.ui.StatusText

class TargetEnvironmentsMasterDetails @JvmOverloads constructor(
  private val project: Project,
  private val initialSelectedName: String? = null,
  private val defaultLanguageRuntime: LanguageRuntimeType<*>?
) : MasterDetailsComponent() {

  private var _lastSelectedConfig: TargetEnvironmentConfiguration? = null
  internal val selectedConfig: TargetEnvironmentConfiguration?
    get() = myCurrentConfigurable?.editableObject as? TargetEnvironmentConfiguration ?: _lastSelectedConfig

  init {
    // note that `MasterDetailsComponent` does not work without `initTree()`
    initTree()
    myTree.emptyText.text = "No targets added"
    myTree.emptyText.appendSecondaryText(ExecutionBundle.message("targets.details.status.text.add.new.target"),
                                         SimpleTextAttributes.LINK_ATTRIBUTES) {
      val popup = ActionManager.getInstance().createActionPopupMenu("TargetEnvironmentsConfigurable.EmptyListText", CreateNewTargetGroup())
      val size = myTree.emptyText.preferredSize
      val textY = myTree.height / if (myTree.emptyText.isShowAboveCenter) 3 else 2
      popup.component.show(myTree, (myTree.width - size.width) / 2, textY + size.height)
    }
    val shortcutText = KeymapUtil.getFirstKeyboardShortcutText(CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.ADD))
    myTree.emptyText.appendSecondaryText(" ($shortcutText)", StatusText.DEFAULT_ATTRIBUTES, null)
  }

  override fun getDisplayName(): String = ExecutionBundle.message("targets.details.configurable.name.remote.targets")

  override fun getEmptySelectionString(): String {
    return ExecutionBundle.message("targets.details.status.text.select.target.to.configure")
  }

  override fun reset() {
    myRoot.removeAllChildren()

    allTargets().forEach { nextTarget -> addTargetNode(nextTarget) }

    super.reset()

    initialSelectedName?.let { selectNodeInTree(initialSelectedName) }
  }

  override fun isModified(): Boolean =
    allTargets().size != getConfiguredTargets().size ||
    deletedTargets().isNotEmpty() ||
    super.isModified()

  override fun createActions(fromPopup: Boolean): List<AnAction> = mutableListOf(
    CreateNewTargetGroup(),
    MyDeleteAction(),
    DuplicateAction()
  )

  override fun processRemovedItems() {
    val deletedTargets = deletedTargets()
    deletedTargets.forEach { TargetEnvironmentsManager.instance.targets.removeConfig(it) }
    super.processRemovedItems()
  }

  override fun wasObjectStored(editableObject: Any?): Boolean {
    return TargetEnvironmentsManager.instance.targets.resolvedConfigs().contains(editableObject)
  }

  private fun deletedTargets(): Set<TargetEnvironmentConfiguration> = allTargets().toSet() - getConfiguredTargets()

  override fun apply() {
    super.apply()

    val addedConfigs = getConfiguredTargets() - TargetEnvironmentsManager.instance.targets.resolvedConfigs()
    addedConfigs.forEach { TargetEnvironmentsManager.instance.addTarget(it) }
  }

  override fun disposeUIResources() {
    _lastSelectedConfig = selectedObject as? TargetEnvironmentConfiguration
    super.disposeUIResources()
  }

  private fun allTargets() = TargetEnvironmentsManager.instance.targets.resolvedConfigs()

  private fun addTargetNode(config: TargetEnvironmentConfiguration): MyNode {
    val configurable = TargetEnvironmentDetailsConfigurable(project, config, defaultLanguageRuntime)
    val node = MyNode(configurable)
    addNode(node, myRoot)
    selectNodeInTree(node)
    return myRoot
  }

  private fun getConfiguredTargets(): List<TargetEnvironmentConfiguration> =
    myRoot.children().asSequence()
      .map { node -> (node as MyNode).configurable?.editableObject as? TargetEnvironmentConfiguration }
      .filterNotNull()
      .toList()

  private inner class CreateNewTargetAction<T : TargetEnvironmentConfiguration>(private val project: Project,
                                                                                private val type: TargetEnvironmentType<T>)
    : DumbAwareAction(type.displayName, null, type.icon) {

    override fun actionPerformed(e: AnActionEvent) {
      val newConfig: TargetEnvironmentConfiguration

      val wizard = TargetEnvironmentWizard.createWizard(project, type, defaultLanguageRuntime)
      if (wizard != null) {
        if (!wizard.showAndGet()) return

        newConfig = wizard.subject
      }
      else {
        newConfig = type.createDefaultConfig()
        type.initializeNewlyCreated(newConfig)
      }

      if (newConfig.displayName.isBlank()) {
        newConfig.displayName = UniqueNameGenerator.generateUniqueName(type.displayName) { curName ->
          getConfiguredTargets().none { it.displayName == curName }
        }
      }
      // there may be not yet stored names
      TargetEnvironmentsManager.instance.ensureUniqueName(newConfig)
      val newNode = addTargetNode(newConfig)
      selectNodeInTree(newNode, true, true)
    }
  }

  private inner class CreateNewTargetGroup : ActionGroup(ExecutionBundle.message("targets.details.action.add.text"),
                                                         "", IconUtil.getAddIcon()),
                                             ActionGroupWithPreselection, DumbAware {
    init {
      registerCustomShortcutSet(CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.ADD), myTree)
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      return TargetEnvironmentType.EXTENSION_NAME.extensionList
        .map { CreateNewTargetAction(project, it) }
        .toArray(AnAction.EMPTY_ARRAY)
    }

    override fun getActionGroup(): ActionGroup {
      return this
    }
  }

  private inner class DuplicateAction : DumbAwareAction(ExecutionBundle.message("targets.details.action.duplicate.text"),
                                                        ExecutionBundle.message("targets.details.action.duplicate.description"),
                                                        PlatformIcons.COPY_ICON) {
    init {
      registerCustomShortcutSet(CommonShortcuts.getDuplicate(), myTree)
    }

    override fun update(e: AnActionEvent) {
      templatePresentation.isEnabled = getSelectedTarget() != null
    }

    override fun actionPerformed(e: AnActionEvent) {
      duplicateSelected()?.let { copy ->
        TargetEnvironmentsManager.instance.addTarget(copy)
        val newNode = addTargetNode(copy)
        selectNodeInTree(newNode, true, true)
      }
    }

    private fun duplicateSelected(): TargetEnvironmentConfiguration? =
      getSelectedTarget()?.let { it.getTargetType().duplicateConfig(it) }

    private fun getSelectedTarget() = selectedNode?.configurable?.editableObject as? TargetEnvironmentConfiguration
  }
}