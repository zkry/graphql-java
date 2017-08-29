package graphql.analysis

import graphql.ExecutionInput
import graphql.TestUtil
import graphql.execution.AbortExecutionException
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters
import graphql.language.Document
import graphql.parser.Parser
import spock.lang.Specification

class MaxQueryComplexityInstrumentationTest extends Specification {

    Document createQuery(String query) {
        Parser parser = new Parser()
        parser.parseDocument(query)
    }


    def "default complexity calculator"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo
                bar: String
            }
            type Foo {
                scalar: String  
                foo: Foo
            }
        """)
        def query = createQuery("""
            {f2: foo {scalar foo{scalar}} f1: foo { foo {foo {foo {foo{foo{scalar}}}}}} }
            """)
        MaxQueryComplexityInstrumentation queryComplexityInstrumentation = new MaxQueryComplexityInstrumentation(10)
        ExecutionInput executionInput = Mock(ExecutionInput)
        InstrumentationValidationParameters validationParameters = new InstrumentationValidationParameters(executionInput, query, schema, null);
        InstrumentationContext instrumentationContext = queryComplexityInstrumentation.beginValidation(validationParameters)
        when:
        instrumentationContext.onEnd(null, null)
        then:
        def e = thrown(AbortExecutionException)
        e.message == "maximum query complexity exceeded 11 > 10"

    }

    def "custom calculator"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo
                bar: String
            }
            type Foo {
                scalar: String  
                foo: Foo
            }
        """)
        def query = createQuery("""
            {foo {scalar }}
            """)
        def calculator = Mock(FieldComplexityCalculator)
        MaxQueryComplexityInstrumentation queryComplexityInstrumentation = new MaxQueryComplexityInstrumentation(5, calculator)
        ExecutionInput executionInput = Mock(ExecutionInput)
        InstrumentationValidationParameters validationParameters = new InstrumentationValidationParameters(executionInput, query, schema, null);
        InstrumentationContext instrumentationContext = queryComplexityInstrumentation.beginValidation(validationParameters)
        when:
        instrumentationContext.onEnd(null, null)

        then:
        1 * calculator.calculate({ FieldComplexityEnvironment env -> env.field.name == "scalar" }, 0) >> 10
        1 * calculator.calculate({ FieldComplexityEnvironment env -> env.field.name == "foo" }, 10) >> 20
        def e = thrown(AbortExecutionException)
        e.message == "maximum query complexity exceeded 20 > 5"

    }

}


