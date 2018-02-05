package com.aurea.testgenerator

import com.aurea.testgenerator.config.ProjectConfiguration
import com.aurea.testgenerator.extensions.Extensions
import com.aurea.testgenerator.generation.PatternToTest
import com.aurea.testgenerator.generation.UnitTestGenerator
import com.aurea.testgenerator.generation.UnitTestMergeEngine
import com.aurea.testgenerator.pattern.PatternMatchEngine
import com.aurea.testgenerator.pattern.PatternMatcher
import com.aurea.testgenerator.source.*
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.assertj.core.api.Assertions.assertThat

abstract class MatcherPipelineTest extends Specification {

    @Rule
    TemporaryFolder folder = new TemporaryFolder()

    ProjectConfiguration cfg = new ProjectConfiguration()
    UnitSource source
    Pipeline pipeline
    UnitTestWriter unitTestWriter
    UnitTestMergeEngine mergeEngine
    UnitTestGenerator unitTestCollector
    PatternMatchEngine patternMatchCollector

    void setupSpec() {
        Extensions.enable()
    }

    void setup() {
        cfg.out = folder.newFolder("test-out").toPath()
        cfg.src = folder.newFolder("src").toPath()
        cfg.testSrc = folder.newFolder("test").toPath()

        source = new PathUnitSource(new JavaSourceFinder(cfg), cfg.src, SourceFilters.empty())
        patternMatchCollector = new PatternMatchEngine([matcher()])
        unitTestCollector = new UnitTestGenerator([patternToTest()])
        mergeEngine = new UnitTestMergeEngine()
        unitTestWriter = new UnitTestWriter(cfg)

        pipeline = new Pipeline(
                source,
                this.patternMatchCollector,
                this.unitTestCollector,
                SourceFilters.empty(),
                this.mergeEngine,
                this.unitTestWriter)
    }

    String onClassCodeExpect(String code, String expectedTest) {
        createTestedCode(code)

        pipeline.start()

        File testFile = cfg.out.resolve('sample').resolve('FooTest.java').toFile()
        assertThat(testFile).describedAs("Expected test to be generated but it wasn't").exists()
        String resultingTest = cfg.out.resolve('sample').resolve('FooTest.java').toFile().text

        assertThat(resultingTest).isEqualToNormalizingWhitespace(expectedTest)
    }

    void onClassCodeDoNotExpectTest(String code) {
        createTestedCode(code)

        pipeline.start()

        File resultingTest = cfg.out.resolve('sample').resolve('FooTest.java').toFile()

        assertThat(resultingTest).doesNotExist()
    }

    private void createTestedCode(String code) {
        File testFile = new File(cfg.src.toFile().absolutePath + "/sample", 'Foo.java')
        testFile.parentFile.mkdirs()
        testFile.write """
        package sample;

        $code
        """
    }

    JavaParserFacade getSolver() {
        JavaParserFacade.get(new CombinedTypeSolver(
                new JavaParserTypeSolver(cfg.src.toFile()),
                new ReflectionTypeSolver()
        ))
    }

    abstract PatternMatcher matcher()

    abstract PatternToTest patternToTest()
}
