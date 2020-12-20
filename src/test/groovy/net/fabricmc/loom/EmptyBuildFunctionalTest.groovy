package net.fabricmc.loom

import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

import static net.fabricmc.loom.BuildUtils.*
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Created by Mitchell Skaggs on 6/10/2019.
 */
class EmptyBuildFunctionalTest extends Specification {
	@Rule
	TemporaryFolder testProjectDir = new TemporaryFolder()
	File settingsFile
	File buildFile
	File propsFile

	def setup() {
		settingsFile = testProjectDir.newFile('settings.gradle')
		buildFile = testProjectDir.newFile('build.gradle')
		propsFile = testProjectDir.newFile('gradle.properties')
	}

	@Unroll
	def "empty build succeeds using Minecraft #mcVersion"() {
		given:
		settingsFile << genSettingsFile("empty-build-functional-test")
		propsFile << BuildUtils.genPropsFile(mcVersion, yarnVersion, loaderVersion, fabricVersion)
		buildFile << BuildUtils.genBuildFile()

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir.root)
				.withArguments('build',"--stacktrace")
				.withPluginClasspath()
				.forwardOutput()
				.build()

		then:
		result.task(":build").outcome == SUCCESS

		where:
		mcVersion | yarnVersion       		| loaderVersion     | fabricVersion
		'1.14'    | '1.14+build.21'   		| '0.4.8+build.155' | '0.3.0+build.183'
		'1.16.2'  | '1.16.2+build.26:v2' 	| '0.9.2+build.206' | '0.19.0+build.398-1.16'
	}
}
