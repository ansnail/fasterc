package top.gradle.fasterc.variant

import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.builder.dependency.JarDependency
import com.android.builder.dependency.LibraryDependency
import com.android.builder.model.AndroidLibrary
import com.android.builder.model.JavaLibrary
import com.android.builder.model.Library
import org.gradle.api.Project
import top.gradle.fasterc.utils.GradleUtils

/**
 * Created by yanjie on 2017-08-16.
 * Describe:依赖类
 */

public class LibDependency {
    public final File jarFile;
    public final Project dependencyProject;
    public final boolean androidLibrary;

    LibDependency(File jarFile, Project dependencyProject, boolean androidLibrary) {
        this.jarFile = jarFile
        this.dependencyProject = dependencyProject
        this.androidLibrary = androidLibrary
    }

    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false
        LibDependency that = (LibDependency) o
        if (jarFile != that.jarFile) return false
        return true
    }

    int hashCode() {
        return (jarFile != null ? jarFile.hashCode() : 0)
    }

    @Override
    public String toString() {
        return "LibDependency{" + "jarFile=" + jarFile + ", dependencyProject=" + dependencyProject +
                ", androidLibrary=" + androidLibrary + '}';
    }

    private static Project getProjectByPath(Collection<Project> allprojects, String path) {
        return allprojects.find { it.path == path }
    }

    /**
     * 解析项目的工程依赖  compile project('xxx')
     * @param project
     * @return
     */
    public static final Set<LibDependency> resolveProjectDependency(Project project, ApplicationVariant apkVariant) {
        Set<LibDependency> libraryDependencySet = new HashSet<>()
        VariantDependencies variantDeps = apkVariant.getVariantData().getVariantDependency();
        if (GradleUtils.GRADLE_VERSION >= "2.3.0") {
            def allDependencies = new HashSet<>()
            allDependencies.addAll(variantDeps.getCompileDependencies().getAllJavaDependencies())
            allDependencies.addAll(variantDeps.getCompileDependencies().getAllAndroidDependencies())

            for (Object dependency : allDependencies) {
                if (dependency.projectPath != null) {
                    def dependencyProject = getProjectByPath(project.rootProject.allprojects,dependency.projectPath);
                    boolean androidLibrary = dependency.getClass().getName() == "com.android.builder.dependency.level2.AndroidDependency";
                    File jarFile
                    if (androidLibrary) {
                        jarFile = dependency.getJarFile()
                    }else {
                        jarFile = dependency.getArtifactFile()
                    }
                    LibDependency libraryDependency = new LibDependency(jarFile,dependencyProject,androidLibrary)
                    libraryDependencySet.add(libraryDependency)
                }
            }
        }else if (GradleUtils.GRADLE_VERSION  >= "2.2.0") {
            Set<org.gradle.platform.base.Library> librarySet = new HashSet<>()
            for (Object jarLibrary : variantDeps.getCompileDependencies().getJarDependencies()) {
                scanDependency(jarLibrary,librarySet)
            }
            for (Object androidLibrary : variantDeps.getCompileDependencies().getAndroidDependencies()) {
                scanDependency(androidLibrary,librarySet)
            }

            for (Library library : librarySet) {
                boolean isAndroidLibrary = (library instanceof AndroidLibrary);
                File jarFile
                def dependencyProject = getProjectByPath(project.rootProject.allprojects,library.getProject());
                if (isAndroidLibrary) {
                    LibraryDependency androidLibrary = library;
                    jarFile = androidLibrary.getJarFile()
                }else {
                    jarFile = library.getJarFile();
                }
                LibDependency libraryDependency = new LibDependency(jarFile,dependencyProject,isAndroidLibrary)
                libraryDependencySet.add(libraryDependency)
            }
        }else {
            Set librarySet = new HashSet<>()
            for (Object jarLibrary : variantDeps.getJarDependencies()) {
                if (jarLibrary.getProjectPath() != null) {
                    librarySet.add(jarLibrary)
                }
            }
            for (Object androidLibrary : variantDeps.getAndroidDependencies()) {
                scanDependency_2_0_0(androidLibrary,librarySet)
            }

            for (Object library : librarySet) {
                boolean isAndroidLibrary = (library instanceof AndroidLibrary);
                File jarFile
                def projectPath = (library instanceof JarDependency) ? library.getProjectPath() : library.getProject()
                def dependencyProject = getProjectByPath(project.rootProject.allprojects,projectPath);
                if (isAndroidLibrary) {
                    LibraryDependency androidLibrary = library;
                    jarFile = androidLibrary.getJarFile()
                }else {
                    jarFile = library.getJarFile();
                }
                LibDependency libraryDependency = new LibDependency(jarFile,dependencyProject,isAndroidLibrary)
                libraryDependencySet.add(libraryDependency)
            }
        }
        return libraryDependencySet
    }


    /**
     * 扫描依赖(2.2.0 <= android-build-version <= 2.3.0)
     * @param library
     * @param libraryDependencies
     */
    private static final void scanDependency(Library library,Set<Library> libraryDependencies) {
        if (library == null) {
            return
        }
        if (library.getProject() == null) {
            return
        }
        if (libraryDependencies.contains(library)) {
            return
        }
        libraryDependencies.add(library)

        if (library instanceof AndroidLibrary) {
            List<Library> libraryList = library.getJavaDependencies()
            if (libraryList != null) {
                for (Library item : libraryList) {
                    scanDependency(item,libraryDependencies)
                }
            }

            libraryList = library.getLibraryDependencies()
            if (libraryList != null) {
                for (Library item : libraryList) {
                    scanDependency(item,libraryDependencies)
                }
            }
        }else if (library instanceof JavaLibrary) {
            List<Library> libraryList = library.getDependencies()

            if (libraryList != null) {
                for (Library item : libraryList) {
                    scanDependency(item,libraryDependencies)
                }
            }
        }
    }


    /**
     * 扫描依赖(2.0.0 <= android-build-version <= 2.2.0)
     * @param library
     * @param libraryDependencies
     */
    private static final void scanDependency_2_0_0(Object library,Set<Library> libraryDependencies) {
        if (library == null) {
            return
        }
        if (library.getProject() == null){
            return
        }
        if (libraryDependencies.contains(library)) {
            return
        }

        libraryDependencies.add(library)
        if (library instanceof AndroidLibrary) {
            List<Library> libraryList = library.getLibraryDependencies()
            if (libraryList != null) {
                for (Library item : libraryList) {
                    scanDependency_2_0_0(item,libraryDependencies)
                }
            }
        }
    }


}
