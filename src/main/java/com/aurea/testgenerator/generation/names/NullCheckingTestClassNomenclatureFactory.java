package com.aurea.testgenerator.generation.names;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("null-checking")
public class NullCheckingTestClassNomenclatureFactory  implements TestClassNomenclatureFactory {
    @Override
    public TestClassNomenclature newTestClassNomenclature() {
        return new NullCheckingTestClassNomenclature();
    }
}
