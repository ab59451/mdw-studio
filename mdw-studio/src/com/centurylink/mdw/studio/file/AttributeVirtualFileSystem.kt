package com.centurylink.mdw.studio.file

import com.centurylink.mdw.activity.types.AdapterActivity
import com.centurylink.mdw.activity.types.GeneralActivity
import com.centurylink.mdw.activity.types.ScriptActivity
import com.centurylink.mdw.draw.ext.rootName
import com.centurylink.mdw.draw.model.WorkflowObj
import com.centurylink.mdw.draw.model.WorkflowType
import com.centurylink.mdw.java.JavaNaming
import com.centurylink.mdw.model.project.Data
import com.centurylink.mdw.model.workflow.Activity
import com.centurylink.mdw.model.workflow.Process
import com.centurylink.mdw.script.ScriptNaming
import com.centurylink.mdw.studio.proj.ProjectSetup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.json.JSONObject

class AttributeVirtualFileSystem : DeprecatedVirtualFileSystem(), NonPhysicalFileSystem {

    internal val virtualFiles = mutableMapOf<String,AttributeVirtualFile>()

    init {
        startEventPropagation()
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    /**
     * Finds a dynamic Java or Script file.
     */
    fun getJavaOrScriptFile(workflowObj: WorkflowObj, contents: String? = null, qualifier: String? = null): AttributeVirtualFile? {
        assert(workflowObj.type == WorkflowType.activity)
        val projectSetup = workflowObj.project as ProjectSetup
        val process = workflowObj.asset as Process
        val implClass = workflowObj.obj.getString("implementor")
        projectSetup.implementors[implClass]?.let { implementor ->
            if (implementor.category == GeneralActivity::class.qualifiedName &&
                    (implClass == Data.Implementors.DYNAMIC_JAVA || workflowObj.getAttribute("Java") != null)) {
                var name = workflowObj.getAttribute("ClassName")
                if (name == null) {
                    name = JavaNaming.getValidClassName(process.rootName + "_" + workflowObj.id)
                }
                val filePath = "${process.packageName}/$name.java"
                val virtualFile = virtualFiles[filePath]
                return if (virtualFile == null) {
                    // newly dragged from toolbox
                    createFile(filePath, workflowObj, "Java", contents, "java", qualifier)
                }
                else {
                    virtualFile.workflowObj = workflowObj
                    if (contents != null) {
                        virtualFile.contents = contents
                    }
                    virtualFile
                }
            }
            else if (implementor.category == ScriptActivity::class.qualifiedName) {
                var name = ScriptNaming.getValidName(process.rootName + "_" + workflowObj.id)
                qualifier?.let { name += "_$it" }
                val filePathNoExt = "${process.packageName}/$name"
                var ext = AttributeVirtualFile.DEFAULT_SCRIPT_EXT
                workflowObj.getAttribute("SCRIPT")?.let { scriptAttr ->
                    ext = AttributeVirtualFile.getScriptExt(scriptAttr)
                }
                val filePath = "$filePathNoExt.$ext"
                val virtualFile = virtualFiles[filePath]
                return if (virtualFile == null) {
                    // newly dragged from toolbox
                    val attrName = when (qualifier) {
                        "Pre" -> "PreScript"
                        "Post" -> "PostScript"
                        else -> "Rule"
                    }
                    createFile("${process.packageName}/$name.$ext", workflowObj, attrName, contents, ext, qualifier)
                }
                else {
                    virtualFile.workflowObj = workflowObj
                    if (contents != null) {
                        virtualFile.contents = contents
                    }
                    virtualFile
                }
            }
        }
        return null
    }

    override fun findFileByPath(path: String): VirtualFile? {
        val activeProject = ProjectSetup.activeProject
        return if (activeProject == null) {
            LOG.warn("Cannot find active project for: $path")
            null
        }
        else {
            findFileByPath(path, activeProject)
        }
    }

    /**
     * Caches found VirtualFiles to avoid this:
     * https://youtrack.jetbrains.com/issue/IDEA-203751
     */
    fun findFileByPath(path: String, project: Project): AttributeVirtualFile? {
        return virtualFiles[path]
    }

    /**
     * Only creates if not already existing.
     */
    private fun createFile(path: String, workflowObj: WorkflowObj, attributeName: String,
            contents: String? = null, ext: String? = null, qualifier: String? = null): AttributeVirtualFile {
        var file = virtualFiles[path]
        if (file == null) {
            file = AttributeVirtualFile(workflowObj, attributeName, contents, ext, qualifier)
            virtualFiles[path] = file
        }
        return file
    }

    override fun refreshAndFindFileByPath(path: String): VirtualFile? {
        return findFileByPath(path)
    }

    fun refresh(projectSetup: ProjectSetup) {
        virtualFiles.clear()
        for (processAsset in projectSetup.findAssetsOfType("proc")) {
            loadAttributeVirtualFiles(projectSetup, processAsset)
        }
    }

    fun removeAttributeVirtualFiles(projectSetup: ProjectSetup, processAsset: Asset) {
        val removePaths = mutableListOf<String>()
        for (path in virtualFiles.keys) {
            if (path.startsWith("${processAsset.pkg.name}/")) {
                if (path.endsWith(".java") || path.endsWith(".kts") || path.endsWith(".groovy") || path.endsWith(".js")) {
                    removePaths.add(path)
                }
            }
        }
        for (removePath in removePaths) {
            virtualFiles.remove(removePath)
        }
    }

    fun loadAttributeVirtualFiles(projectSetup: ProjectSetup, processAsset: Asset) {
        val contents = String(processAsset.contents)
        if (!contents.startsWith("{")) {
            return  // newly created process without any content
        }
        val process = Process(JSONObject(contents))
        process.name = processAsset.rootName
        process.packageName = processAsset.pkg.name
        process.id = processAsset.id
        for (activity in process.activities) {
            scanActivity(projectSetup, process, activity);
        }
    }

    private fun scanActivity(projectSetup: ProjectSetup, process: Process, activity: Activity) {
        val workflowObj = WorkflowObj(projectSetup, process, WorkflowType.activity, activity.json, false)
        activity.getAttribute("Java")?.let { java ->
            var name = workflowObj.getAttribute("ClassName")
            if (name == null) {
                name = JavaNaming.getValidClassName(process.rootName + "_" + workflowObj.id)
            }
            createFile("${process.packageName}/$name.java", workflowObj, "Java", java)
            return
        }
        activity.getAttribute("Rule")?.let { rule ->
            projectSetup.implementors[workflowObj.obj.getString("implementor")]?.let { implementor ->
                if (implementor.category == ScriptActivity::class.qualifiedName) {
                    val name = ScriptNaming.getValidName(process.rootName + "_" + workflowObj.id)
                    var ext = AttributeVirtualFile.DEFAULT_SCRIPT_EXT
                    workflowObj.getAttribute("SCRIPT")?.let { scriptAttr ->
                        ext = AttributeVirtualFile.getScriptExt(scriptAttr)
                    }
                    createFile("${process.packageName}/$name.$ext", workflowObj, "Rule", rule, ext)
                    return
                }
            }
        }
        activity.getAttribute("PreScript")?.let { script ->
            projectSetup.implementors[workflowObj.obj.getString("implementor")]?.let { implementor ->
                if (implementor.category == AdapterActivity::class.qualifiedName) {
                    val name = ScriptNaming.getValidName(process.rootName + "_" + workflowObj.id)
                    var ext = AttributeVirtualFile.DEFAULT_SCRIPT_EXT
                    workflowObj.getAttribute("PreScriptLang")?.let { langAttr ->
                        ext = AttributeVirtualFile.getScriptExt(langAttr)
                    }
                    createFile("${process.packageName}/$name.$ext", workflowObj, "PreScript", script, ext, "Pre")
                    return
                }
            }
        }
        activity.getAttribute("PostScript")?.let { script ->
            projectSetup.implementors[workflowObj.obj.getString("implementor")]?.let { implementor ->
                if (implementor.category == AdapterActivity::class.qualifiedName) {
                    val name = ScriptNaming.getValidName(process.rootName + "_" + workflowObj.id)
                    var ext = AttributeVirtualFile.DEFAULT_SCRIPT_EXT
                    workflowObj.getAttribute("PostScriptLang")?.let { langAttr ->
                        ext = AttributeVirtualFile.getScriptExt(langAttr)
                    }
                    createFile("${process.packageName}/$name.$ext", workflowObj, "PostScript", script, ext, "Post")
                    return
                }
            }
        }
    }

    override fun refresh(asynchronous: Boolean) {
        val activeProject = ProjectSetup.activeProject
        if (activeProject == null) {
            LOG.warn("Cannot find active project")
        }
        else {
            activeProject.getComponent(ProjectSetup::class.java)?.let { projectSetup ->
                refresh(projectSetup)
            }
        }
    }

    companion object {
        val LOG = Logger.getInstance(AttributeVirtualFileSystem::class.java)
        const val PROTOCOL = "mdw"
        val instance: AttributeVirtualFileSystem
            get() = VirtualFileManager.getInstance().getFileSystem(PROTOCOL) as AttributeVirtualFileSystem
    }
}