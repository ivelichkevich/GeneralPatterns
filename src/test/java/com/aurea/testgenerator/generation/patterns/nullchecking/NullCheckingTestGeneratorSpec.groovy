package com.aurea.testgenerator.generation.patterns.nullchecking;

import com.aurea.testgenerator.MatcherPipelineTest
import com.aurea.testgenerator.generation.TestGenerator

public class NullCheckingTestGeneratorSpec extends MatcherPipelineTest {
    def "test_OneMethodOneParameter"() {
        expect:
        onClassCodeExpectByPattern """
            import java.util.Objects;
            
            public class PanelLittle {
            
                private Long id;
            
                public Long getId() {
                    return id;
                }
            
                public void setId(Long id) {
                    this.id = Objects.requireNonNull(id);
                }
            }
        """, """     
            package sample;
            
            import java\\.util\\.Objects;
            import javax\\.annotation\\.Generated;
            import org\\.junit\\.Test;
            
            @Generated\\("GeneralPatterns"\\)
            public class FooPatternTest \\{
            
                @Test\\(expected \\= NullPointerException\\.class\\)
                public void test_setId_NullPointerException_[0-9a-zA-Z]+\\(\\) \\{
                    PanelLittle o \\= new PanelLittle\\(\\);
                    o\\.setId\\(null\\);
                \\}
            \\}
        """
    }

    def "test_OneMethodTwoParameters"() {
        expect:
        onClassCodeExpectByPattern """
            import java.util.Objects;
            
            public class PanelLittle {
            
                private Long id;
            
                private String serial;
            
                public Long getId() {
                    return id;
                }
            
                public void setId(Long id) {
                    this.id = Objects.requireNonNull(id);
                }

                public void setup(Long id, String serial) {
                    Objects.requireNonNull(id);
                    setId(id);
                    this.serial = Objects.requireNonNull(serial);
                }
            }
        """, """     
            package sample;
             
            import java\\.util\\.Objects;
            import javax\\.annotation\\.Generated;
            import org\\.junit\\.Test;
             
            @Generated\\("GeneralPatterns"\\)
            public class FooPatternTest \\{
             
                @Test\\(expected \\= NullPointerException\\.class\\)
                public void test_setId_NullPointerException_[0-9a-zA-Z]+\\(\\) \\{
                    PanelLittle o \\= new PanelLittle\\(\\);
                    o\\.setId\\(null\\);
                \\}
                
                @Test\\(expected \\= NullPointerException\\.class\\)
                public void test_setup_NullPointerException_[0-9a-zA-Z]+\\(\\) \\{
                    PanelLittle o \\= new PanelLittle\\(\\);
                    o\\.setup\\(null, "[0-9a-zA-Z]+"\\);
                \\}
                
                @Test\\(expected \\= NullPointerException\\.class\\)
                public void test_setup_NullPointerException_[0-9a-zA-Z]+\\(\\) \\{
                    PanelLittle o \\= new PanelLittle\\(\\);
                    o\\.setup\\([0-9]+L, null\\);
                \\}
            \\}
        """
    }

    def "test_ConstructorTwoParameters"() {
        expect:
        onClassCodeExpectByPattern """
            import static java.util.Objects.requireNonNull;
            import java.util.Objects;
            
            public class PanelLittle {
            
                private Long id;
            
                private String serial;
            
                public PanelLittle(Long id, String serial) {
                    this.id = requireNonNull(id);
                    this.serial = requireNonNull(serial);
                }
            }
        """, """     
            package sample;
             
            import java\\.util\\.Objects;
            import javax\\.annotation\\.Generated;
            import org\\.junit\\.Test;
            import static java\\.util\\.Objects\\.requireNonNull;
            
            @Generated\\("GeneralPatterns"\\)
            public class FooPatternTest \\{
            
                @Test\\(expected \\= NullPointerException\\.class\\)
                public void test_PanelLittle_NullPointerException_[0-9a-zA-Z]+\\(\\) \\{
                    new PanelLittle\\(null, "[0-9a-zA-Z]+"\\);
                \\}
            
                @Test\\(expected \\= NullPointerException\\.class\\)
                public void test_PanelLittle_NullPointerException_[0-9a-zA-Z]+\\(\\) \\{
                    new PanelLittle\\([0-9]+L, null\\);
                \\}
            \\}
        """
    }

    @Override
    TestGenerator generator() {
        NullCheckingTestGenerator generator = new NullCheckingTestGenerator()
        generator.setCoverageReporter(visitReporter)
        generator.setReporter(reporter)
        NullCheckingMethodTestBuilder methodTestBuilder = new NullCheckingMethodTestBuilder();
        methodTestBuilder.setValueFactory(valueFactory)
        generator.setMethodBuilder(methodTestBuilder)
        NullCheckingConstructorTestBuilder constructorTestBuilder = new NullCheckingConstructorTestBuilder();
        constructorTestBuilder.setValueFactory(valueFactory)
        generator.setConstructorBuilder(constructorTestBuilder)
        generator
    }
}
