# Changelog

## 1.0.0 (2025-08-11)


### Features

* Adaptation of oncology DTOs and mappings to changed KDK schemata ([9120ef9](https://github.com/dnpm-dip/central-data-node/commit/9120ef9cec49ce248ce0dc7b5bed03d409e5fadf))
* Adaptations of MTB-/RD-mappings; Various bug fixes or improvements to MVHReportingService and DNPMConnectorImpl ([da6646f](https://github.com/dnpm-dip/central-data-node/commit/da6646f1fc53c34d53db436b2978f375783caa74))
* Added hitherto missing field Metadata.localCaseId; Adapted mappings to use new extension methods on PAtientRecord; Adapted default value for RDCase.onsetDate ([40b0187](https://github.com/dnpm-dip/central-data-node/commit/40b0187cb02c83ac5509615337b6dcc05e05269e))
* Added OncologyMolecular and other types to match oncology submission schemata ([738cc27](https://github.com/dnpm-dip/central-data-node/commit/738cc27268e889af582150ac7dc0176255561cc1))
* Added RD DTOs; Corrections/adaptations to MTB DTOs and mappings ([dcd3e54](https://github.com/dnpm-dip/central-data-node/commit/dcd3e54ae3842a87140cb135afacde89509d2636))
* Added SubmissionReport.libraryType mapping, given that the issue reported on BfArM repo that this shouldn't pertain to the clinical data report has been ignored so far ([e6d366f](https://github.com/dnpm-dip/central-data-node/commit/e6d366f6a3e9e161def8ce2ff33cdcffe7fd4fe9))
* Correction to SubmissionReport; Corrections/improvements to tests and test uploader ([01d84d5](https://github.com/dnpm-dip/central-data-node/commit/01d84d58d58dcf221e6f654b9b687b0c333075ba))
* Further work on MTB mappings ([ddb25f0](https://github.com/dnpm-dip/central-data-node/commit/ddb25f0ddb6e4d08e4ae4ee022e84f46d6730cf1))
* Improvements to mapping implementations ([8ce3eab](https://github.com/dnpm-dip/central-data-node/commit/8ce3eabd86778ad9146443a2ba38004155f06a74))
* Initial updating work of RD DTOs to latest specs; Adaptations of mappings pending! ([7ac8bcd](https://github.com/dnpm-dip/central-data-node/commit/7ac8bcdaf16f70ae5e1ad5c21bad204e52745c09))
* Made whole URL of BfArM API configurable instead of just the baseURL; Temporarily added mapping of TEST SubmissionReports ([14b5fc1](https://github.com/dnpm-dip/central-data-node/commit/14b5fc1250ae0ea31b5b058e99fb4df33947736d))
* MTBMappings provisionally completed ([f268828](https://github.com/dnpm-dip/central-data-node/commit/f2688282e98a7c26f57440e7ffe9aae414f1ed71))
* Prelimimary complete mapping for RD clinical data; Added SubmissionReport mapping ([c1deb12](https://github.com/dnpm-dip/central-data-node/commit/c1deb12c9b9342bf14a199978dc181904acbdc7a))
* Refactored token provision in BfArM connector implementation ([e2e95f4](https://github.com/dnpm-dip/central-data-node/commit/e2e95f41d31ae2a4fcdeaedcd5ac405bf01bd38d))
* Updated bfarm DTOs and Mappings; Initial work on configuring a polling start time in addition to mere polling period ([72d3814](https://github.com/dnpm-dip/central-data-node/commit/72d3814435282d021c7a4aa4ea1e621496bfebff))
* Updated RD DTOs and mappings ([28fa4fe](https://github.com/dnpm-dip/central-data-node/commit/28fa4fed99dad52c17cc6f372dedfd4372932aeb))
* Work on model object for oncological submission ([f4ef252](https://github.com/dnpm-dip/central-data-node/commit/f4ef25242d08dc8663ec80feb096f3aaa45965bc))
* Work on MVH mappings ([7b8ad8b](https://github.com/dnpm-dip/central-data-node/commit/7b8ad8b0620f3cf6406be23f8f615f26ee26c3e6))
* Work on RD data mappings ([fe98877](https://github.com/dnpm-dip/central-data-node/commit/fe988771bdecf867764934cfca9ba637db5ce87a))


### Bug Fixes

* Adapted RDMappings to changed cardinality of RDDiagnosis.onsetDate ([99c9d22](https://github.com/dnpm-dip/central-data-node/commit/99c9d22191df134848797af3322e7d04d52d2e5f))
* Added recovery to avoid short-circuited Future.traverse; Improved logging output ([dcc579e](https://github.com/dnpm-dip/central-data-node/commit/dcc579e5a645edc5997741af27a44c2a0806f6c6))
* Corrected cardinality of Metadata.localCaseId ([62bb1de](https://github.com/dnpm-dip/central-data-node/commit/62bb1de2b06b54ea578b1ace48e8bd99a9bdb503))
* Finished configurability of polling start time ([9b14307](https://github.com/dnpm-dip/central-data-node/commit/9b14307f05814817ef235fe416a01ba1cf7e22ae))
* Fixed logical flaw in polling execution delay when startTime is defined; Improved error handling in DNPMConnectorImpl ([288d843](https://github.com/dnpm-dip/central-data-node/commit/288d84330f1dae4cbd7d164d64c7fdebc99e0278))
