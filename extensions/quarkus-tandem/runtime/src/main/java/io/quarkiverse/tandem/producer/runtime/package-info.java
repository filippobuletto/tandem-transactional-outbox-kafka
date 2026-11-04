/**
 * The extension's runtime internals: the recorder, the configuration mappings, the repository adapter
 * and every bean implementation. Quarkus needs some of these types to be public; none of them is part
 * of the extension's API, and any of them may change in any release (LLD-quarkus §3.1). An application
 * reads Tandem's settings through the {@code tandem.*} keys, never through these mappings.
 */
package io.quarkiverse.tandem.producer.runtime;
