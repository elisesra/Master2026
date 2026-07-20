package experiment.core;

import experiment.prompts.FewShot1PromptTemplate;
import experiment.prompts.FewShot2PromptTemplate;
import experiment.prompts.ChainOfThought1PromptTemplate;
import experiment.prompts.ChainOfThought2PromptTemplate;
import experiment.prompts.Rag1PromptTemplate;
import experiment.prompts.Rag2PromptTemplate;
import experiment.prompts.ReAct1PromptTemplate;
import experiment.prompts.ReAct2PromptTemplate;
import experiment.prompts.Panacea1PromptTemplate;
import experiment.prompts.Panacea2PromptTemplate;
import experiment.prompts.PanaceaRun1PromptTemplate;
import experiment.prompts.PanaceaRun2PromptTemplate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ExperimentHandlerTest {

    @Test
    void selectsIndependentFewShotTemplates() {
        ExperimentHandler handler = new ExperimentHandler();

        assertInstanceOf(FewShot1PromptTemplate.class, handler.selectPrompt("fs1"));
        assertInstanceOf(FewShot1PromptTemplate.class, handler.selectPrompt("fs"));
        assertInstanceOf(FewShot2PromptTemplate.class, handler.selectPrompt("fs2"));
        assertInstanceOf(ChainOfThought1PromptTemplate.class, handler.selectPrompt("cot"));
        assertInstanceOf(ChainOfThought1PromptTemplate.class, handler.selectPrompt("cot1"));
        assertInstanceOf(ChainOfThought2PromptTemplate.class, handler.selectPrompt("cot2"));
        assertInstanceOf(Rag1PromptTemplate.class, handler.selectPrompt("rag"));
        assertInstanceOf(Rag1PromptTemplate.class, handler.selectPrompt("rag1"));
        assertInstanceOf(Rag2PromptTemplate.class, handler.selectPrompt("rag2"));
        assertInstanceOf(ReAct1PromptTemplate.class, handler.selectPrompt("react"));
        assertInstanceOf(ReAct1PromptTemplate.class, handler.selectPrompt("react1"));
        assertInstanceOf(ReAct2PromptTemplate.class, handler.selectPrompt("react2"));
        assertInstanceOf(Panacea1PromptTemplate.class, handler.selectPrompt("panacea"));
        assertInstanceOf(Panacea1PromptTemplate.class, handler.selectPrompt("panacea1"));
        assertInstanceOf(Panacea2PromptTemplate.class, handler.selectPrompt("panacea2"));
        assertInstanceOf(PanaceaRun1PromptTemplate.class, handler.selectPrompt("panacea_run1"));
        assertInstanceOf(PanaceaRun1PromptTemplate.class, handler.selectPrompt("panacea-run1"));
        assertInstanceOf(PanaceaRun2PromptTemplate.class, handler.selectPrompt("panacea_run2"));
        assertInstanceOf(PanaceaRun2PromptTemplate.class, handler.selectPrompt("panacea-run2"));
    }
}
