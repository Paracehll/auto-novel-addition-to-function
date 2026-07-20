import type { GlobalGlossaryInfo, GlobalGlossaryHistory, GlobalGlossaryTerms } from '@/model/GlobalGlossary';
import { client } from './client';

const listGlobalGlossariesInfo = (used?: boolean, ids?: string) =>
  client.get('global-glossary', {
    searchParams: {
      ...(used ? { used: '1' } : {}),
      ...(ids ? { ids } : {}),
    }
  }).json<GlobalGlossaryInfo[]>();

const getGlobalGlossaryTerms = (id: string) =>
  client.get(`global-glossary/${id}/terms`).json<GlobalGlossaryTerms>();

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
  getGlobalGlossaryHistory,
  createGlobalGlossary,
  updateGlobalGlossary,
  deleteGlobalGlossary,
  deleteGlobalGlossaryRecord,
};
