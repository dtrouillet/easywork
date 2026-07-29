package fr.easywork;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class EasyworkApplicationTests {

	ApplicationModules modules = ApplicationModules.of(EasyworkApplication.class);

	@Test
	void modulesAreStructurallyValid() {
		modules.verify();
	}

	@Test
	void documentModuleStructure() {
		new Documenter(modules).writeDocumentation();
	}
}
