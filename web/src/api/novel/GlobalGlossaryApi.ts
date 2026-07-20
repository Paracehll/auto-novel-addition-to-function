import type { GlobalGlossaryInfo, GlobalGlossaryHistory, GlobalGlossaryTerms, GlobalGlossaryVersion } from '@/model/GlobalGlossary';
import { GlobalGlossaryCacheRepo } from '@/repos/useGlobalGlossaryCache';
import { client } from './client';

const listGlobalGlossariesInfo = (used?: boolean, ids?: string) =>
  client.get('global-glossary', {
    searchParams: {
      ...(used ? { used: '1' } : {}),
      ...(ids ? { ids } : {}),
    }
  }).json<GlobalGlossaryInfo[]>();

const getGlobalGlossaryVersion = (id: string) =>
  client.get(`global-glossary/${id}/version`).json<GlobalGlossaryVersion>();

const getGlobalGlossaryTerms = async (id: string): Promise<GlobalGlossaryTerms> => {
  try {
    const cached = await GlobalGlossaryCacheRepo.get(id);
    if (cached) {
      const { version: serverVersion } = await getGlobalGlossaryVersion(id);
      if (serverVersion === cached.version) {
        return cached;
      }
    }
  } catch (e) {
    console.warn(`Failed to read/verify cached global glossary terms for ${id}, fetching fresh.`, e);
  }
  const fresh = await client.get(`global-glossary/${id}/terms`).json<GlobalGlossaryTerms>();
  try {
    await GlobalGlossaryCacheRepo.set(fresh);
  } catch (e) {
    console.warn(`Failed to save fresh global glossary terms to cache for ${id}`, e);
  }
  return fresh;
};

const getGlobalGlossaryHistory = (id: string) =>
  client.get(`global-glossary/${id}/history`).json<GlobalGlossaryHistory>();

const createGlobalGlossary = (json: {
  name: string;
  content: { [key: string]: string };
  tag: string[];
}) => client.post('global-glossary', { json }).json<GlobalGlossaryInfo>();

const updateGlobalGlossary = (
  id: string,
  json: {
    name: string;
    content: { [key: string]: string };
    tag: string[];
  },
) => client.put(`global-glossary/${id}`, { json }).json<GlobalGlossaryInfo>();

const deleteGlobalGlossary = (id: string) =>
  client.delete(`global-glossary/${id}`);

const deleteGlobalGlossaryRecord = (id: string, index: number) =>
  client.delete(`global-glossary/${id}/record/${index}`);

export const GlobalGlossaryApi = {
  listGlobalGlossariesInfo,
  getGlobalGlossaryTerms,
  getGlobalGlossaryVersion,
  getGlobalGlossaryHistory,
  createGlobalGlossary,
  updateGlobalGlossary,
  deleteGlobalGlossary,
  deleteGlobalGlossaryRecord,
};
